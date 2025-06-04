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
 * Component for managing gRPC connection pools.
 * Maintains multiple connections for each target service to improve
 * performance and reliability.
 */
@Component
public class ConnectionPoolManager {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolManager.class);

    // Connection pool configuration
    private static final int MIN_CONNECTIONS_PER_TARGET = 2;
    private static final int MAX_CONNECTIONS_PER_TARGET = 8;
    private static final int CONNECTION_WARMUP_TIMEOUT_SECONDS = 30;

    // Connection pools for each target
    private final Map<String, ChannelPool> channelPools = new ConcurrentHashMap<>();

    /**
     * Get a connection to the specified target.
     *
     * @param targetKey unique identifier of the target service
     * @param hostname  target host name
     * @param port      target port
     * @param useTls    whether to use TLS
     * @param sslContext SSL context when using TLS
     * @return available ManagedChannel
     */
    public ManagedChannel getChannel(String targetKey, String hostname, int port, boolean useTls, SslContext sslContext) {
        ChannelPool pool = channelPools.computeIfAbsent(targetKey,
                k -> new ChannelPool(targetKey, hostname, port, useTls, sslContext));

        return pool.getChannel();
    }

    /**
     * Remove the connection pool for the given target.
     *
     * @param targetKey unique identifier of the target service
     */
    public void removeChannelPool(String targetKey) {
        ChannelPool pool = channelPools.remove(targetKey);
        if (pool != null) {
            pool.shutdown();
            logger.info("Removed connection pool for target: {}", targetKey);
        }
    }

    /**
     * Get statistics of a connection pool.
     *
     * @param targetKey unique identifier of the target service
     * @return pool statistics
     */
    public PoolStatistics getPoolStatistics(String targetKey) {
        ChannelPool pool = channelPools.get(targetKey);
        return pool != null ? pool.getStatistics() : null;
    }

    /**
     * Get statistics of all connection pools.
     *
     * @return statistics of all pools
     */
    public Map<String, PoolStatistics> getAllPoolStatistics() {
        Map<String, PoolStatistics> stats = new ConcurrentHashMap<>();
        channelPools.forEach((key, pool) -> stats.put(key, pool.getStatistics()));
        return stats;
    }

    /**
     * Shut down all connection pools
     */
    public void shutdown() {
        logger.info("Shutting down all connection pools...");
        channelPools.values().forEach(ChannelPool::shutdown);
        channelPools.clear();
        logger.info("All connection pools shut down");
    }

    /**
     * Connection pool implementation for a single target service
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

            // Initialize the pool asynchronously
            initializePool();
        }

        /**
         * Initialize the pool
         */
        private void initializePool() {
            if (initLock.tryLock()) {
                try {
                    if (!initialized && !shutdown) {
                        logger.debug("Initializing connection pool for target: {}", targetKey);

                        // Create the minimum number of connections first
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
         * Create a new ManagedChannel
         */
        private ManagedChannel createChannel() {
            try {
                ManagedChannel channel;

                if (useTls && sslContext != null) {
                    // Create a TLS connection
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
                    // Create a plaintext connection
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
         * Get an available connection
         */
        public ManagedChannel getChannel() {
            if (shutdown) {
                throw new IllegalStateException("Connection pool for " + targetKey + " has been shut down");
            }

            // Ensure the pool is initialized
            if (!initialized) {
                initializePool();
            }

            totalRequests.incrementAndGet();

            // Load balancing: round-robin selection
            int index = Math.abs(currentIndex.getAndIncrement() % MAX_CONNECTIONS_PER_TARGET);

            // If there is no connection at this index, try to create one
            if (channels[index] == null) {
                synchronized (this) {
                    if (channels[index] == null && !shutdown) {
                        channels[index] = createChannel();
                        logger.debug("Created additional channel at index {} for target: {}", index, targetKey);
                    }
                }
            }

            ManagedChannel channel = channels[index];

            // Check channel state and create a new one if closed
            if (channel != null && (channel.isShutdown() || channel.isTerminated())) {
                synchronized (this) {
                    if (channels[index] == channel) { // double check
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
         * Get connection pool statistics
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
         * Shut down the connection pool
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
     * Connection pool statistics
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