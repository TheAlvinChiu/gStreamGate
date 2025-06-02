package io.github.alvinchiu.gstreamgate.manager;

import io.github.alvinchiu.gstreamgate.circuit.CircuitBreakerManager;
import io.github.alvinchiu.gstreamgate.entity.GrpcProxyMap;
import io.github.alvinchiu.gstreamgate.event.ProxyConfigChangedEvent;
import io.github.alvinchiu.gstreamgate.handler.HostBasedHandlerRegistry;
import io.github.alvinchiu.gstreamgate.metrics.ProxyMetrics;
import io.github.alvinchiu.gstreamgate.optimization.MemoryOptimizer;
import io.github.alvinchiu.gstreamgate.pool.ConnectionPoolManager;
import io.github.alvinchiu.gstreamgate.repository.GrpcProxyMapRepository;
import io.github.alvinchiu.gstreamgate.security.TlsCertificateManager;
import io.grpc.HandlerRegistry;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 優化版動態 gRPC 代理管理器
 * 整合了連接池、熔斷器、Metrics 收集和內存優化功能
 */
@Component
public class OptimizedDynamicGrpcProxyManager {
    private static final Logger logger = LoggerFactory.getLogger(OptimizedDynamicGrpcProxyManager.class);

    // 核心依賴
    private final GrpcProxyMapRepository grpcProxyMapRepository;
    private final TlsCertificateManager tlsCertificateManager;
    private final ApplicationEventPublisher eventPublisher;

    // 優化組件
    private final ConnectionPoolManager connectionPoolManager;
    private final CircuitBreakerManager circuitBreakerManager;
    private final ProxyMetrics proxyMetrics;
    private final MemoryOptimizer memoryOptimizer;

    // 狀態管理
    private final Map<String, GrpcProxyMap> activeProxyMappings = new ConcurrentHashMap<>();
    private final Map<String, ProxyStatus> proxyStatusMap = new ConcurrentHashMap<>();
    private HostBasedHandlerRegistry handlerRegistry;

    // 後台任務
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(3);

    @Autowired
    public OptimizedDynamicGrpcProxyManager(
            GrpcProxyMapRepository grpcProxyMapRepository,
            ApplicationEventPublisher eventPublisher,
            TlsCertificateManager tlsCertificateManager,
            ConnectionPoolManager connectionPoolManager,
            CircuitBreakerManager circuitBreakerManager,
            ProxyMetrics proxyMetrics,
            MemoryOptimizer memoryOptimizer) {

        this.grpcProxyMapRepository = grpcProxyMapRepository;
        this.eventPublisher = eventPublisher;
        this.tlsCertificateManager = tlsCertificateManager;
        this.connectionPoolManager = connectionPoolManager;
        this.circuitBreakerManager = circuitBreakerManager;
        this.proxyMetrics = proxyMetrics;
        this.memoryOptimizer = memoryOptimizer;
    }

    @PostConstruct
    public void initialize() {
        logger.info("Initializing optimized dynamic gRPC proxy manager");
        try {
            refreshProxyMappings();
            startBackgroundTasks();
        } catch (Exception e) {
            logger.error("Error initializing optimized proxy manager: " + e.getMessage(), e);
            // 創建空的處理器註冊表以確保應用程序可以啟動
            handlerRegistry = new HostBasedHandlerRegistry(new HashMap<>());
        }
    }

    /**
     * 刷新所有代理映射
     */
    public synchronized void refreshProxyMappings() {
        logger.info("Refreshing proxy mappings from database with optimizations");
        Instant startTime = Instant.now();

        try {
            // 獲取所有啟用的代理映射
            List<GrpcProxyMap> enabledMappings = grpcProxyMapRepository.findByEnable("Y");
            logger.info("Found {} enabled proxy mappings", enabledMappings.size());

            // 移除不再啟用的映射
            Set<String> newProxyHostnames = enabledMappings.stream()
                    .map(GrpcProxyMap::getProxyHostName)
                    .collect(Collectors.toSet());

            // 查找要移除的主機名
            Set<String> hostnamesForRemoval = activeProxyMappings.keySet().stream()
                    .filter(hostname -> !newProxyHostnames.contains(hostname))
                    .collect(Collectors.toSet());

            // 移除不再需要的映射
            for (String hostname : hostnamesForRemoval) {
                removeProxyMapping(hostname, false);
            }

            // 創建新的通道映射
            Map<String, ManagedChannel> newChannelMap = new ConcurrentHashMap<>();

            for (GrpcProxyMap mapping : enabledMappings) {
                try {
                    String targetKey = createTargetKey(mapping);
                    ManagedChannel channel = createOptimizedChannel(mapping, targetKey);

                    if (channel != null) {
                        newChannelMap.put(mapping.getProxyHostName(), channel);
                        activeProxyMappings.put(mapping.getProxyHostName(), mapping);

                        // 初始化代理狀態
                        proxyStatusMap.put(mapping.getProxyHostName(),
                                new ProxyStatus(mapping.getProxyHostName(), targetKey, true));

                        // 記錄連接 metrics
                        proxyMetrics.recordConnection(targetKey, true);

                        logger.debug("Created optimized channel for: {} -> {}",
                                mapping.getProxyHostName(), targetKey);
                    }
                } catch (Exception e) {
                    logger.error("Failed to create channel for mapping: " + mapping.getProxyHostName(), e);
                    // 記錄錯誤 metrics
                    proxyMetrics.recordError(mapping.getProxyHostName(), "CHANNEL_CREATION_ERROR", e.getMessage());
                }
            }

            // 創建新的處理器註冊表
            handlerRegistry = new HostBasedHandlerRegistry(newChannelMap);

            Duration refreshDuration = Duration.between(startTime, Instant.now());
            logger.info("Proxy mapping refresh completed in {}ms. Active channels: {}",
                    refreshDuration.toMillis(), newChannelMap.size());

            // 發布刷新事件
            eventPublisher.publishEvent(ProxyConfigChangedEvent.refreshEvent());

            // 記錄 metrics
            proxyMetrics.recordRequest("REFRESH_MAPPINGS", "SYSTEM", refreshDuration, true, 0);

        } catch (Exception e) {
            Duration refreshDuration = Duration.between(startTime, Instant.now());
            logger.error("Error refreshing proxy mappings: " + e.getMessage(), e);
            proxyMetrics.recordRequest("REFRESH_MAPPINGS", "SYSTEM", refreshDuration, false, 0);
            throw e;
        }
    }

    /**
     * 創建優化的通道
     */
    private ManagedChannel createOptimizedChannel(GrpcProxyMap mapping, String targetKey) {
        try {
            // 使用熔斷器保護通道創建
            return circuitBreakerManager.execute(targetKey, () -> {
                // 確定是否使用 TLS
                boolean useTls = determineTlsUsage(mapping);
                SslContext sslContext = null;

                if (useTls) {
                    sslContext = createSslContext(mapping);
                }

                // 使用連接池管理器獲取通道
                return connectionPoolManager.getChannel(
                        targetKey,
                        mapping.getTargetHostName(),
                        mapping.getTargetPort(),
                        useTls,
                        sslContext
                );
            });
        } catch (CircuitBreakerManager.CircuitBreakerOpenException e) {
            logger.warn("Circuit breaker is open for target: {}, skipping channel creation", targetKey);
            proxyMetrics.recordError(targetKey, "CIRCUIT_BREAKER_OPEN", e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Failed to create optimized channel for target: " + targetKey, e);
            proxyMetrics.recordError(targetKey, "CHANNEL_CREATION_FAILED", e.getMessage());
            throw e;
        }
    }

    /**
     * 確定 TLS 使用情況
     */
    private boolean determineTlsUsage(GrpcProxyMap mapping) {
        String secureMode = mapping.getSecureMode();
        if (secureMode == null || secureMode.isEmpty()) {
            secureMode = "AUTO";
        }

        switch (secureMode) {
            case "SECURE":
                return true;
            case "PLAINTEXT":
                return false;
            case "AUTO":
            default:
                // 使用熔斷器保護的 TLS 檢測
                try {
                    return circuitBreakerManager.execute("tls-detection:" + mapping.getTargetHostName(),
                            () -> detectTlsSupport(mapping.getTargetHostName(), mapping.getTargetPort()));
                } catch (Exception e) {
                    logger.warn("TLS detection failed for {}:{}, defaulting to plaintext",
                            mapping.getTargetHostName(), mapping.getTargetPort());
                    return false;
                }
        }
    }

    /**
     * 檢測 TLS 支持（簡化版）
     */
    private boolean detectTlsSupport(String hostname, int port) {
        // 這裡可以實現實際的 TLS 檢測邏輯
        // 為了示例，我們返回 false（plaintext）
        return false;
    }

    /**
     * 創建 SSL 上下文
     */
    private SslContext createSslContext(GrpcProxyMap mapping) {
        try {
            if ("Y".equals(mapping.getAutoTrustUpstreamCerts())) {
                return tlsCertificateManager.createInsecureClientSslContext();
            } else if (mapping.getTrustedCertsContent() != null && !mapping.getTrustedCertsContent().isEmpty()) {
                return tlsCertificateManager.createClientSslContext(mapping.getTrustedCertsContent());
            } else {
                return tlsCertificateManager.createInsecureClientSslContext();
            }
        } catch (Exception e) {
            logger.error("Failed to create SSL context for mapping: " + mapping.getProxyHostName(), e);
            throw new RuntimeException("SSL context creation failed", e);
        }
    }

    /**
     * 創建目標鍵
     */
    private String createTargetKey(GrpcProxyMap mapping) {
        return mapping.getTargetHostName() + ":" + mapping.getTargetPort();
    }

    /**
     * 移除代理映射
     */
    private void removeProxyMapping(String proxyHostname, boolean publishEvent) {
        GrpcProxyMap mapping = activeProxyMappings.remove(proxyHostname);
        if (mapping != null) {
            String targetKey = createTargetKey(mapping);

            // 移除連接池
            connectionPoolManager.removeChannelPool(targetKey);

            // 記錄斷開連接 metrics
            proxyMetrics.recordConnection(targetKey, false);

            // 移除狀態
            proxyStatusMap.remove(proxyHostname);

            logger.info("Removed proxy mapping: {}", proxyHostname);

            if (publishEvent) {
                eventPublisher.publishEvent(
                        new ProxyConfigChangedEvent(ProxyConfigChangedEvent.ChangeType.REMOVED, proxyHostname));
            }
        }
    }

    /**
     * 啟動後台任務
     */
    private void startBackgroundTasks() {
        // 定期健康檢查
        scheduledExecutor.scheduleAtFixedRate(this::performHealthChecks, 30, 30, TimeUnit.SECONDS);

        // 定期 metrics 報告
        scheduledExecutor.scheduleAtFixedRate(this::reportMetrics, 60, 60, TimeUnit.SECONDS);

        // 定期內存清理
        scheduledExecutor.scheduleAtFixedRate(this::performMemoryCleanup, 300, 300, TimeUnit.SECONDS);

        logger.info("Background tasks started");
    }

    /**
     * 執行健康檢查
     */
    private void performHealthChecks() {
        try {
            for (Map.Entry<String, ProxyStatus> entry : proxyStatusMap.entrySet()) {
                String proxyHostname = entry.getKey();
                ProxyStatus status = entry.getValue();

                // 使用熔斷器進行健康檢查
                try {
                    boolean healthy = circuitBreakerManager.execute(status.getTargetKey(),
                            () -> checkProxyHealth(status));

                    status.updateHealth(healthy, healthy ? "Health check passed" : "Health check failed");

                } catch (CircuitBreakerManager.CircuitBreakerOpenException e) {
                    status.updateHealth(false, "Circuit breaker is open");
                    logger.debug("Health check skipped for {} due to open circuit breaker", proxyHostname);
                }
            }
        } catch (Exception e) {
            logger.error("Error during health checks", e);
        }
    }

    /**
     * 檢查代理健康狀態
     */
    private boolean checkProxyHealth(ProxyStatus status) {
        // 這裡可以實現實際的健康檢查邏輯
        // 例如發送一個簡單的 ping 請求
        return true; // 簡化實現
    }

    /**
     * 報告 metrics
     */
    private void reportMetrics() {
        try {
            ProxyMetrics.MetricsSummary summary = proxyMetrics.getSummary();
            logger.info("Proxy metrics summary: {}", summary);

            // 報告連接池狀態
            Map<String, ConnectionPoolManager.PoolStatistics> poolStats =
                    connectionPoolManager.getAllPoolStatistics();

            if (!poolStats.isEmpty()) {
                logger.info("Connection pool statistics:");
                poolStats.forEach((key, stats) ->
                        logger.info("  {}: {}", key, stats));
            }

            // 報告熔斷器狀態
            Map<String, CircuitBreakerManager.CircuitBreakerStatus> circuitStats =
                    circuitBreakerManager.getAllCircuitBreakerStatus();

            if (!circuitStats.isEmpty()) {
                logger.info("Circuit breaker statistics:");
                circuitStats.forEach((key, stats) -> {
                    if (stats.getState() != CircuitBreakerManager.State.CLOSED) {
                        logger.warn("  {}: {}", key, stats);
                    } else {
                        logger.debug("  {}: {}", key, stats);
                    }
                });
            }

            // 報告內存統計
            MemoryOptimizer.MemoryStatistics memStats = memoryOptimizer.getMemoryStatistics();
            logger.info("Memory statistics: {}", memStats);

        } catch (Exception e) {
            logger.error("Error reporting metrics", e);
        }
    }

    /**
     * 執行內存清理
     */
    private void performMemoryCleanup() {
        try {
            logger.debug("Performing periodic memory cleanup");
            memoryOptimizer.cleanupCaches();

            // 檢查內存使用情況
            MemoryOptimizer.MemoryStatistics memStats = memoryOptimizer.getMemoryStatistics();
            if (memStats.getMemoryUsagePercentage() > 85) {
                logger.warn("High memory usage detected ({}%), performing aggressive cleanup",
                        memStats.getMemoryUsagePercentage());
                memoryOptimizer.forceCleanup();
            }

        } catch (Exception e) {
            logger.error("Error during memory cleanup", e);
        }
    }

    /**
     * 獲取處理器註冊表
     */
    public HandlerRegistry getHandlerRegistry() {
        return handlerRegistry;
    }

    /**
     * 獲取活躍代理主機名列表
     */
    public List<String> getActiveProxyHostnames() {
        return new ArrayList<>(activeProxyMappings.keySet());
    }

    /**
     * 獲取代理映射
     */
    public GrpcProxyMap getProxyMapping(String proxyHostname) {
        return activeProxyMappings.get(proxyHostname);
    }

    /**
     * 獲取所有代理狀態
     */
    public Map<String, ProxyStatus> getAllProxyStatus() {
        return new HashMap<>(proxyStatusMap);
    }

    /**
     * 添加代理映射
     */
    public synchronized void addProxyMapping(GrpcProxyMap mapping) {
        logger.info("Adding new proxy mapping: {}", mapping.getProxyHostName());
        if ("Y".equals(mapping.getEnable())) {
            try {
                String targetKey = createTargetKey(mapping);
                ManagedChannel channel = createOptimizedChannel(mapping, targetKey);

                if (channel != null) {
                    activeProxyMappings.put(mapping.getProxyHostName(), mapping);
                    proxyStatusMap.put(mapping.getProxyHostName(),
                            new ProxyStatus(mapping.getProxyHostName(), targetKey, true));

                    // 重新創建處理器註冊表
                    refreshHandlerRegistry();

                    // 記錄 metrics
                    proxyMetrics.recordConnection(targetKey, true);

                    // 發布事件
                    eventPublisher.publishEvent(new ProxyConfigChangedEvent(
                            ProxyConfigChangedEvent.ChangeType.ADDED, mapping.getProxyHostName()));
                }
            } catch (Exception e) {
                logger.error("Failed to add proxy mapping: " + mapping.getProxyHostName(), e);
                proxyMetrics.recordError(mapping.getProxyHostName(), "ADD_MAPPING_FAILED", e.getMessage());
            }
        }
    }

    /**
     * 更新代理映射
     */
    public synchronized void updateProxyMapping(GrpcProxyMap mapping) {
        logger.info("Updating proxy mapping: {}", mapping.getProxyHostName());

        if ("Y".equals(mapping.getEnable())) {
            // 先移除舊映射
            removeProxyMapping(mapping.getProxyHostName(), false);
            // 添加新映射
            addProxyMapping(mapping);
        } else {
            // 禁用映射
            removeProxyMapping(mapping.getProxyHostName(), true);
        }
    }

    /**
     * 刷新處理器註冊表
     */
    private void refreshHandlerRegistry() {
        Map<String, ManagedChannel> channelMap = new ConcurrentHashMap<>();

        for (Map.Entry<String, GrpcProxyMap> entry : activeProxyMappings.entrySet()) {
            String proxyHostname = entry.getKey();
            GrpcProxyMap mapping = entry.getValue();
            String targetKey = createTargetKey(mapping);

            try {
                ManagedChannel channel = connectionPoolManager.getChannel(
                        targetKey, mapping.getTargetHostName(), mapping.getTargetPort(),
                        determineTlsUsage(mapping), createSslContext(mapping));

                if (channel != null) {
                    channelMap.put(proxyHostname, channel);
                }
            } catch (Exception e) {
                logger.error("Failed to get channel for {}", proxyHostname, e);
            }
        }

        handlerRegistry = new HostBasedHandlerRegistry(channelMap);
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down optimized dynamic gRPC proxy manager");

        // 停止後台任務
        scheduledExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 清理連接池
        connectionPoolManager.shutdown();

        logger.info("Optimized dynamic gRPC proxy manager shutdown completed");
    }

    /**
     * 代理狀態類
     */
    public static class ProxyStatus {
        private final String proxyHostname;
        private final String targetKey;
        private volatile boolean healthy;
        private volatile String lastHealthMessage;
        private volatile Instant lastHealthCheck;
        private final Instant createdTime;

        public ProxyStatus(String proxyHostname, String targetKey, boolean healthy) {
            this.proxyHostname = proxyHostname;
            this.targetKey = targetKey;
            this.healthy = healthy;
            this.lastHealthMessage = "Initialized";
            this.lastHealthCheck = Instant.now();
            this.createdTime = Instant.now();
        }

        public void updateHealth(boolean healthy, String message) {
            this.healthy = healthy;
            this.lastHealthMessage = message;
            this.lastHealthCheck = Instant.now();
        }

        // Getters
        public String getProxyHostname() { return proxyHostname; }
        public String getTargetKey() { return targetKey; }
        public boolean isHealthy() { return healthy; }
        public String getLastHealthMessage() { return lastHealthMessage; }
        public Instant getLastHealthCheck() { return lastHealthCheck; }
        public Instant getCreatedTime() { return createdTime; }

        @Override
        public String toString() {
            return String.format("ProxyStatus{proxy='%s', target='%s', healthy=%s, message='%s', lastCheck=%s}",
                    proxyHostname, targetKey, healthy, lastHealthMessage, lastHealthCheck);
        }
    }
}