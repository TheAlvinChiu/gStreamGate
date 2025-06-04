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
 * Optimized dynamic gRPC proxy manager
 * Integrates connection pooling, circuit breaking, metrics collection and
 * memory optimization
 */
@Component
public class OptimizedDynamicGrpcProxyManager {
    private static final Logger logger = LoggerFactory.getLogger(OptimizedDynamicGrpcProxyManager.class);

    // Core dependencies
    private final GrpcProxyMapRepository grpcProxyMapRepository;
    private final TlsCertificateManager tlsCertificateManager;
    private final ApplicationEventPublisher eventPublisher;

    // Optimization components
    private final ConnectionPoolManager connectionPoolManager;
    private final CircuitBreakerManager circuitBreakerManager;
    private final ProxyMetrics proxyMetrics;
    private final MemoryOptimizer memoryOptimizer;

    // State management
    private final Map<String, GrpcProxyMap> activeProxyMappings = new ConcurrentHashMap<>();
    private final Map<String, ProxyStatus> proxyStatusMap = new ConcurrentHashMap<>();
    private HostBasedHandlerRegistry handlerRegistry;

    // Background tasks
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
            // Create an empty handler registry to allow the application to start
            handlerRegistry = new HostBasedHandlerRegistry(new HashMap<>());
        }
    }

    /**
     * Refresh all proxy mappings
     */
    public synchronized void refreshProxyMappings() {
        logger.info("Refreshing proxy mappings from database with optimizations");
        Instant startTime = Instant.now();

        try {
            // Retrieve all enabled proxy mappings
            List<GrpcProxyMap> enabledMappings = grpcProxyMapRepository.findByEnable("Y");
            logger.info("Found {} enabled proxy mappings", enabledMappings.size());

            // Remove mappings that are no longer enabled
            Set<String> newProxyHostnames = enabledMappings.stream()
                    .map(GrpcProxyMap::getProxyHostName)
                    .collect(Collectors.toSet());

            // Find hostnames that should be removed
            Set<String> hostnamesForRemoval = activeProxyMappings.keySet().stream()
                    .filter(hostname -> !newProxyHostnames.contains(hostname))
                    .collect(Collectors.toSet());

            // Remove mappings that are no longer needed
            for (String hostname : hostnamesForRemoval) {
                removeProxyMapping(hostname, false);
            }

            // Create new channel mapping
            Map<String, ManagedChannel> newChannelMap = new ConcurrentHashMap<>();

            for (GrpcProxyMap mapping : enabledMappings) {
                try {
                    String targetKey = createTargetKey(mapping);
                    ManagedChannel channel = createOptimizedChannel(mapping, targetKey);

                    if (channel != null) {
                        newChannelMap.put(mapping.getProxyHostName(), channel);
                        activeProxyMappings.put(mapping.getProxyHostName(), mapping);

                        // Initialize proxy status
                        proxyStatusMap.put(mapping.getProxyHostName(),
                                new ProxyStatus(mapping.getProxyHostName(), targetKey, true));

                        // Record connection metrics
                        proxyMetrics.recordConnection(targetKey, true);

                        logger.debug("Created optimized channel for: {} -> {}",
                                mapping.getProxyHostName(), targetKey);
                    }
                } catch (Exception e) {
                    logger.error("Failed to create channel for mapping: " + mapping.getProxyHostName(), e);
                    // Record error metrics
                    proxyMetrics.recordError(mapping.getProxyHostName(), "CHANNEL_CREATION_ERROR", e.getMessage());
                }
            }

            // Create a new handler registry
            handlerRegistry = new HostBasedHandlerRegistry(newChannelMap);

            Duration refreshDuration = Duration.between(startTime, Instant.now());
            logger.info("Proxy mapping refresh completed in {}ms. Active channels: {}",
                    refreshDuration.toMillis(), newChannelMap.size());

            // Publish refresh event
            eventPublisher.publishEvent(ProxyConfigChangedEvent.refreshEvent());

            // Record metrics
            proxyMetrics.recordRequest("REFRESH_MAPPINGS", "SYSTEM", refreshDuration, true, 0);

        } catch (Exception e) {
            Duration refreshDuration = Duration.between(startTime, Instant.now());
            logger.error("Error refreshing proxy mappings: " + e.getMessage(), e);
            proxyMetrics.recordRequest("REFRESH_MAPPINGS", "SYSTEM", refreshDuration, false, 0);
            throw e;
        }
    }

    /**
     * Create an optimized channel
     */
    private ManagedChannel createOptimizedChannel(GrpcProxyMap mapping, String targetKey) {
        try {
            // Create the channel with circuit breaker protection
            return circuitBreakerManager.execute(targetKey, () -> {
                // Determine whether TLS should be used
                boolean useTls = determineTlsUsage(mapping);
                SslContext sslContext = null;

                if (useTls) {
                    sslContext = createSslContext(mapping);
                }

                // Acquire the channel from the connection pool manager
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
     * Determine TLS usage
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
                // TLS detection protected by a circuit breaker
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
     * Check TLS support (simplified)
     */
    private boolean detectTlsSupport(String hostname, int port) {
        // Actual TLS detection logic can be implemented here
        // For demonstration we return false (plaintext)
        return false;
    }

    /**
     * Create the SSL context
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
     * Create the target key
     */
    private String createTargetKey(GrpcProxyMap mapping) {
        return mapping.getTargetHostName() + ":" + mapping.getTargetPort();
    }

    /**
     * Remove a proxy mapping
     */
    private void removeProxyMapping(String proxyHostname, boolean publishEvent) {
        GrpcProxyMap mapping = activeProxyMappings.remove(proxyHostname);
        if (mapping != null) {
            String targetKey = createTargetKey(mapping);

            // Remove the connection pool
            connectionPoolManager.removeChannelPool(targetKey);

            // Record disconnection metrics
            proxyMetrics.recordConnection(targetKey, false);

            // Remove status entry
            proxyStatusMap.remove(proxyHostname);

            logger.info("Removed proxy mapping: {}", proxyHostname);

            if (publishEvent) {
                eventPublisher.publishEvent(
                        new ProxyConfigChangedEvent(ProxyConfigChangedEvent.ChangeType.REMOVED, proxyHostname));
            }
        }
    }

    /**
     * Start background tasks
     */
    private void startBackgroundTasks() {
        // Periodic health checks
        scheduledExecutor.scheduleAtFixedRate(this::performHealthChecks, 30, 30, TimeUnit.SECONDS);

        // Periodic metrics reporting
        scheduledExecutor.scheduleAtFixedRate(this::reportMetrics, 60, 60, TimeUnit.SECONDS);

        // Periodic memory cleanup
        scheduledExecutor.scheduleAtFixedRate(this::performMemoryCleanup, 300, 300, TimeUnit.SECONDS);

        logger.info("Background tasks started");
    }

    /**
     * Perform health checks
     */
    private void performHealthChecks() {
        try {
            for (Map.Entry<String, ProxyStatus> entry : proxyStatusMap.entrySet()) {
                String proxyHostname = entry.getKey();
                ProxyStatus status = entry.getValue();

                // Perform health check with circuit breaker
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
     * Check the health of a proxy
     */
    private boolean checkProxyHealth(ProxyStatus status) {
        // Real health check logic can be implemented here
        // For example, send a simple ping request
        return true; // Simplified implementation
    }

    /**
     * Report metrics
     */
    private void reportMetrics() {
        try {
            ProxyMetrics.MetricsSummary summary = proxyMetrics.getSummary();
            logger.info("Proxy metrics summary: {}", summary);

            // Report connection pool status
            Map<String, ConnectionPoolManager.PoolStatistics> poolStats =
                    connectionPoolManager.getAllPoolStatistics();

            if (!poolStats.isEmpty()) {
                logger.info("Connection pool statistics:");
                poolStats.forEach((key, stats) ->
                        logger.info("  {}: {}", key, stats));
            }

            // Report circuit breaker status
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

            // Report memory statistics
            MemoryOptimizer.MemoryStatistics memStats = memoryOptimizer.getMemoryStatistics();
            logger.info("Memory statistics: {}", memStats);

        } catch (Exception e) {
            logger.error("Error reporting metrics", e);
        }
    }

    /**
     * Perform memory cleanup
     */
    private void performMemoryCleanup() {
        try {
            logger.debug("Performing periodic memory cleanup");
            memoryOptimizer.cleanupCaches();

            // Check memory usage
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
     * Get the handler registry
     */
    public HandlerRegistry getHandlerRegistry() {
        return handlerRegistry;
    }

    /**
     * Get the list of active proxy hostnames
     */
    public List<String> getActiveProxyHostnames() {
        return new ArrayList<>(activeProxyMappings.keySet());
    }

    /**
     * Get a proxy mapping
     */
    public GrpcProxyMap getProxyMapping(String proxyHostname) {
        return activeProxyMappings.get(proxyHostname);
    }

    /**
     * Get all proxy status information
     */
    public Map<String, ProxyStatus> getAllProxyStatus() {
        return new HashMap<>(proxyStatusMap);
    }

    /**
     * Add a proxy mapping
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

                    // Recreate the handler registry
                    refreshHandlerRegistry();

                    // Record metrics
                    proxyMetrics.recordConnection(targetKey, true);

                    // Publish event
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
     * Update a proxy mapping
     */
    public synchronized void updateProxyMapping(GrpcProxyMap mapping) {
        logger.info("Updating proxy mapping: {}", mapping.getProxyHostName());

        if ("Y".equals(mapping.getEnable())) {
            // Remove the old mapping first
            removeProxyMapping(mapping.getProxyHostName(), false);
            // Add the new mapping
            addProxyMapping(mapping);
        } else {
            // Disable the mapping
            removeProxyMapping(mapping.getProxyHostName(), true);
        }
    }

    /**
     * Refresh the handler registry
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

        // Stop background tasks
        scheduledExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Clean up connection pools
        connectionPoolManager.shutdown();

        logger.info("Optimized dynamic gRPC proxy manager shutdown completed");
    }

    /**
     * Proxy status class
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