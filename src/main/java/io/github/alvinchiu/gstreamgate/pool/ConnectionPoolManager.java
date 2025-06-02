package io.github.alvinchiu.gstreamgate.pool;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 管理 gRPC 連接池的組件
 * 為每個目標服務維護多個連接以提高性能和可靠性
 */
@Component
public class ConnectionPoolManager {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolManager.class);

    // 連接池配置
    private static final int MIN_CONNECTIONS_PER_TARGET = 2;
    private static final int MAX_CONNECTIONS_PER_TARGET = 8;
    private static final int CONNECTION_WARMUP_TIMEOUT_SECONDS = 30;

    // 存儲每個目標的連接池
    private final Map<String, ChannelPool> channelPools = new ConcurrentHashMap<>();

    /**
     * 獲取到指定目標的連接
     *
     * @param targetKey 目標服務的唯一標識
     * @param hostname 目標主機名
     * @param port 目標端口
     * @param useTls 是否使用 TLS
     * @param sslContext SSL 上下文（如果使用 TLS）
     * @return 可用的 ManagedChannel
     */
    public ManagedChannel getChannel(String targetKey, String hostname, int port, boolean useTls, SslContext sslContext) {
        ChannelPool pool = channelPools.computeIfAbsent(targetKey,
                k -> new ChannelPool(targetKey, hostname, port, useTls, sslContext));

        return pool.getChannel();
    }

    /**
     * 移除指定目標的連接池
     *
     * @param targetKey 目標服務的唯一標識
     */
    public void removeChannelPool(String targetKey) {
        ChannelPool pool = channelPools.remove(targetKey);
        if (pool != null) {
            pool.shutdown();
            logger.info("Removed connection pool for target: {}", targetKey);
        }
    }

    /**
     * 獲取連接池統計信息
     *
     * @param targetKey 目標服務的唯一標識
     * @return 連接池統計信息
     */
    public PoolStatistics getPoolStatistics(String targetKey) {
        ChannelPool pool = channelPools.get(targetKey);
        return pool != null ? pool.getStatistics() : null;
    }

    /**
     * 獲取所有連接池的統計信息
     *
     * @return 所有連接池的統計信息
     */
    public Map<String, PoolStatistics> getAllPoolStatistics() {
        Map<String, PoolStatistics> stats = new ConcurrentHashMap<>();
        channelPools.forEach((key, pool) -> stats.put(key, pool.getStatistics()));
        return stats;
    }

    /**
     * 關閉所有連接池
     */
    public void shutdown() {
        logger.info("Shutting down all connection pools...");
        channelPools.values().forEach(ChannelPool::shutdown);
        channelPools.clear();
        logger.info("All connection pools shut down");
    }

    /**
     * 單個目標服務的連接池實現
     */
    private static class ChannelPool {
        private final Logger logger = LoggerFactory.getLogger(ChannelPool.class);

        private final String targetKey;
        private final String hostname;
        private final int port;
        private final boolean useTls;
        private final SslContext sslContext;

        private final ManagedChannel[] channels;
        private final AtomicInteger currentIndex = new AtomicInteger(0);
        private final AtomicInteger totalRequests = new AtomicInteger(0);
        private final ReentrantLock initLock = new ReentrantLock();

        private volatile boolean initialized = false;
        private volatile boolean shutdown = false;

        public ChannelPool(String targetKey, String hostname, int port, boolean useTls, SslContext sslContext) {
            this.targetKey = targetKey;
            this.hostname = hostname;
            this.port = port;
            this.useTls = useTls;
            this.sslContext = sslContext;
            this.channels = new ManagedChannel[MAX_CONNECTIONS_PER_TARGET];

            // 異步初始化連接池
            initializePool();
        }

        /**
         * 初始化連接池
         */
        private void initializePool() {
            if (initLock.tryLock()) {
                try {
                    if (!initialized && !shutdown) {
                        logger.debug("Initializing connection pool for target: {}", targetKey);

                        // 先創建最小數量的連接
                        for (int i = 0; i < MIN_CONNECTIONS_PER_TARGET; i++) {
                            channels[i] = createChannel();
                        }

                        initialized = true;
                        logger.info("Connection pool initialized for target: {} with {} connections",
                                targetKey, MIN_CONNECTIONS_PER_TARGET);
                    }
                } finally {
                    initLock.unlock();
                }
            }
        }

        /**
         * 創建新的 ManagedChannel
         */
        private ManagedChannel createChannel() {
            try {
                ManagedChannel channel;

                if (useTls && sslContext != null) {
                    // 創建 TLS 連接
                    channel = NettyChannelBuilder
                            .forAddress(hostname, port)
                            .sslContext(sslContext)
                            .keepAliveTime(120, TimeUnit.SECONDS)
                            .keepAliveTimeout(30, TimeUnit.SECONDS)
                            .keepAliveWithoutCalls(false)
                            .maxInboundMessageSize(20 * 1024 * 1024)
                            .flowControlWindow(2 * 1024 * 1024)
                            .build();
                } else {
                    // 創建 plaintext 連接
                    channel = ManagedChannelBuilder
                            .forAddress(hostname, port)
                            .usePlaintext()
                            .keepAliveTime(120, TimeUnit.SECONDS)
                            .keepAliveTimeout(30, TimeUnit.SECONDS)
                            .keepAliveWithoutCalls(false)
                            .maxInboundMessageSize(20 * 1024 * 1024)
                            .build();
                }

                logger.debug("Created new channel for target: {} (TLS: {})", targetKey, useTls);
                return channel;

            } catch (Exception e) {
                logger.error("Failed to create channel for target: " + targetKey, e);
                throw new RuntimeException("Failed to create channel", e);
            }
        }

        /**
         * 獲取可用的連接
         */
        public ManagedChannel getChannel() {
            if (shutdown) {
                throw new IllegalStateException("Connection pool for " + targetKey + " has been shut down");
            }

            // 確保連接池已初始化
            if (!initialized) {
                initializePool();
            }

            totalRequests.incrementAndGet();

            // 負載均衡：輪詢選擇連接
            int index = Math.abs(currentIndex.getAndIncrement() % MAX_CONNECTIONS_PER_TARGET);

            // 如果該位置沒有連接，嘗試創建新連接
            if (channels[index] == null) {
                synchronized (this) {
                    if (channels[index] == null && !shutdown) {
                        channels[index] = createChannel();
                        logger.debug("Created additional channel at index {} for target: {}", index, targetKey);
                    }
                }
            }

            ManagedChannel channel = channels[index];

            // 檢查連接狀態，如果連接已關閉，創建新連接
            if (channel != null && (channel.isShutdown() || channel.isTerminated())) {
                synchronized (this) {
                    if (channels[index] == channel) { // 雙重檢查
                        channels[index] = createChannel();
                        logger.debug("Replaced dead channel at index {} for target: {}", index, targetKey);
                        channel = channels[index];
                    }
                }
            }

            if (channel == null) {
                throw new RuntimeException("No available connection for target: " + targetKey);
            }

            return channel;
        }

        /**
         * 獲取連接池統計信息
         */
        public PoolStatistics getStatistics() {
            int activeConnections = 0;
            int totalConnections = 0;

            for (ManagedChannel channel : channels) {
                if (channel != null) {
                    totalConnections++;
                    if (!channel.isShutdown() && !channel.isTerminated()) {
                        activeConnections++;
                    }
                }
            }

            return new PoolStatistics(
                    targetKey,
                    activeConnections,
                    totalConnections,
                    MAX_CONNECTIONS_PER_TARGET,
                    totalRequests.get(),
                    initialized,
                    shutdown
            );
        }

        /**
         * 關閉連接池
         */
        public void shutdown() {
            if (shutdown) {
                return;
            }

            initLock.lock();
            try {
                shutdown = true;
                logger.debug("Shutting down connection pool for target: {}", targetKey);

                for (int i = 0; i < channels.length; i++) {
                    ManagedChannel channel = channels[i];
                    if (channel != null) {
                        try {
                            channel.shutdown();
                            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                                channel.shutdownNow();
                            }
                        } catch (InterruptedException e) {
                            channel.shutdownNow();
                            Thread.currentThread().interrupt();
                        }
                        channels[i] = null;
                    }
                }

                logger.info("Connection pool shut down for target: {}", targetKey);
            } finally {
                initLock.unlock();
            }
        }
    }

    /**
     * 連接池統計信息
     */
    public static class PoolStatistics {
        private final String targetKey;
        private final int activeConnections;
        private final int totalConnections;
        private final int maxConnections;
        private final int totalRequests;
        private final boolean initialized;
        private final boolean shutdown;

        public PoolStatistics(String targetKey, int activeConnections, int totalConnections,
                              int maxConnections, int totalRequests, boolean initialized, boolean shutdown) {
            this.targetKey = targetKey;
            this.activeConnections = activeConnections;
            this.totalConnections = totalConnections;
            this.maxConnections = maxConnections;
            this.totalRequests = totalRequests;
            this.initialized = initialized;
            this.shutdown = shutdown;
        }

        // Getters
        public String getTargetKey() { return targetKey; }
        public int getActiveConnections() { return activeConnections; }
        public int getTotalConnections() { return totalConnections; }
        public int getMaxConnections() { return maxConnections; }
        public int getTotalRequests() { return totalRequests; }
        public boolean isInitialized() { return initialized; }
        public boolean isShutdown() { return shutdown; }

        public double getUtilizationRate() {
            return maxConnections > 0 ? (double) totalConnections / maxConnections : 0.0;
        }

        @Override
        public String toString() {
            return String.format("PoolStatistics{target='%s', active=%d, total=%d, max=%d, requests=%d, utilization=%.2f%%}",
                    targetKey, activeConnections, totalConnections, maxConnections, totalRequests, getUtilizationRate() * 100);
        }
    }
}