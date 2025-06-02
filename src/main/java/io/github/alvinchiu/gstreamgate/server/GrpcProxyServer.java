package io.github.alvinchiu.gstreamgate.server;

import io.github.alvinchiu.gstreamgate.config.GrpcProxyProperties;
import io.github.alvinchiu.gstreamgate.entity.GrpcProxyMap;
import io.github.alvinchiu.gstreamgate.event.ProxyConfigChangedEvent;
import io.github.alvinchiu.gstreamgate.event.UpstreamHealthCheckEvent;
import io.github.alvinchiu.gstreamgate.handler.HostBasedHandlerRegistry;
import io.github.alvinchiu.gstreamgate.manager.DynamicGrpcProxyManager;
import io.github.alvinchiu.gstreamgate.repository.GrpcProxyMapRepository;
import io.github.alvinchiu.gstreamgate.util.ExecutorManager;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * gRPC proxy server component
 * Handles starting and managing the gRPC server that proxies requests
 */
@Component
public class GrpcProxyServer {
    private static final Logger logger = LoggerFactory.getLogger(GrpcProxyServer.class);

    private final GrpcProxyProperties proxyProperties;
    private final DynamicGrpcProxyManager proxyManager;
    private final SslContext sslContext; // Injected SslContext
    private final GrpcProxyMapRepository grpcProxyMapRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ExecutorManager executorManager;

    // Health check related fields
    private final Map<String, UpstreamServiceStatus> upstreamStatusMap = new ConcurrentHashMap<>();
    private ScheduledFuture<?> healthCheckTask;
    private ScheduledFuture<?> statisticsTask;

    // Statistics tracking
    private final AtomicInteger totalCallsProcessed = new AtomicInteger(0);
    private final AtomicInteger failedCallsCount = new AtomicInteger(0);
    private final AtomicInteger tlsErrorsCount = new AtomicInteger(0);
    private final Map<String, AtomicInteger> errorsByHostname = new ConcurrentHashMap<>();

    private Server server;
    private final ReentrantLock serverLock = new ReentrantLock();
    private final int serverPort;
    private boolean healthCheckEnabled = true;

    @Autowired
    public GrpcProxyServer(GrpcProxyProperties proxyProperties,
                           DynamicGrpcProxyManager proxyManager,
                           GrpcProxyMapRepository grpcProxyMapRepository,
                           ApplicationEventPublisher eventPublisher,
                           ExecutorManager executorManager,
                           @Autowired(required = false) SslContext sslContext) {
        this.proxyProperties = proxyProperties;
        this.proxyManager = proxyManager;
        this.grpcProxyMapRepository = grpcProxyMapRepository;
        this.eventPublisher = eventPublisher;
        this.executorManager = executorManager;
        this.sslContext = sslContext; // May be null
        this.serverPort = proxyProperties.getServer().getPort();

        // Log server initialization
        logger.info("Initializing gRPC proxy server on port " + serverPort);
    }

    /**
     * Start the gRPC proxy server
     */
    @PostConstruct
    public void start() throws IOException {
        serverLock.lock();
        try {
            logger.info("Starting gRPC proxy server...");

            // Configure and start the server
            ServerBuilder<?> serverBuilder;

            // Check if TLS is enabled and SslContext is available
            if (sslContext != null && proxyProperties.getTls().isEnabled()) {
                // Use the injected SslContext with improved HTTP/2 settings
                serverBuilder = NettyServerBuilder.forAddress(new InetSocketAddress("0.0.0.0", serverPort))
                        .intercept(createLoggingInterceptor())
                        .sslContext(sslContext)
                        // Modify HTTP/2 related settings, solve too_many_pings issue
                        .keepAliveTime(120, TimeUnit.SECONDS)              // Increase to 120 seconds
                        .keepAliveTimeout(30, TimeUnit.SECONDS)            // Increase to 30 seconds
                        .permitKeepAliveWithoutCalls(true)                 // Allow keepalive without calls
                        .permitKeepAliveTime(60, TimeUnit.SECONDS)         // Set minimum keepalive interval
                        .maxConnectionAge(600, TimeUnit.SECONDS)           // Increase connection lifetime
                        .maxConnectionAgeGrace(120, TimeUnit.SECONDS)      // Increase shutdown grace period
                        .maxInboundMessageSize(20 * 1024 * 1024)           // Increase message size limit
                        .maxInboundMetadataSize(16 * 1024);                // Set metadata size limit

                logger.info("Starting gRPC proxy server with TLS on port " + serverPort + " and enhanced HTTP/2 settings");
                logger.debug("TLS configuration: enabled=true, sslContext=" + (sslContext != null ? "provided" : "null"));
            } else {
                // Use plain server with improved HTTP/2 settings
                serverBuilder = ServerBuilder.forPort(serverPort)
                        .intercept(createLoggingInterceptor())
                        // Set HTTP/2 parameters for plain server too
                        .maxInboundMessageSize(20 * 1024 * 1024)
                        .maxInboundMetadataSize(16 * 1024);

                logger.info("Starting gRPC proxy server without TLS on port " + serverPort + " with enhanced message size limits");
                logger.debug("TLS configuration: enabled=" +
                        (proxyProperties.getTls().isEnabled() ? "true" : "false") +
                        ", sslContext=" + (sslContext != null ? "provided" : "null"));
            }

            // Get current handler registry
            var registry = proxyManager.getHandlerRegistry();

            // Log all registered upstream services
            if (registry instanceof HostBasedHandlerRegistry) {
                HostBasedHandlerRegistry hostRegistry = (HostBasedHandlerRegistry) registry;
                Set<String> hostnames = hostRegistry.getHostnames();

                if (hostnames.isEmpty()) {
                    logger.warn("No upstream services registered. Proxy server will start but won't forward any requests");
                } else {
                    logger.info("Registered upstream services (" + hostnames.size() + "):");
                    for (String hostname : hostnames) {
                        GrpcProxyMap mapping = proxyManager.getProxyMapping(hostname);
                        if (mapping != null) {
                            logUpstreamServiceDetails("Registered", mapping);

                            // Initialize status tracking for this upstream service
                            upstreamStatusMap.put(hostname, new UpstreamServiceStatus(
                                    hostname,
                                    mapping.getTargetHostName(),
                                    mapping.getTargetPort(),
                                    mapping.getSecureMode()
                            ));
                        } else {
                            logger.warn("Upstream service registered but details not found: " + hostname);
                        }
                    }

                    // Schedule health check for registered services
                    scheduleHealthChecks();
                }
            }

            // Set fallback handler registry, all requests are routed through this registry
            serverBuilder.fallbackHandlerRegistry(registry);

            // Build and start the server
            server = serverBuilder.build().start();

            logger.info("gRPC Proxy server started successfully on port " + serverPort +
                    (sslContext != null && proxyProperties.getTls().isEnabled() ? " (with TLS)" : " (no TLS)") +
                    " and enhanced HTTP/2 settings");

            // Log system resource details
            logSystemResources();

        } catch (IOException e) {
            logger.error("Failed to start gRPC proxy server on port " + serverPort + ": " + e.getMessage(), e);
            throw new IOException("Failed to start gRPC proxy server: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error starting gRPC proxy server: " + e.getMessage(), e);
            throw new IOException("Unexpected error starting gRPC proxy server: " + e.getMessage(), e);
        } finally {
            serverLock.unlock();
        }

        // Add shutdown hook to ensure server closes correctly when application exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered, stopping gRPC proxy server...");
            stop();
        }));

        // Schedule periodic statistics logging
        schedulePeriodicTasks();
    }

    /**
     * 調度定期任務
     */
    private void schedulePeriodicTasks() {
        try {
            // 檢查執行器是否可用
            if (!executorManager.isAvailable()) {
                logger.warn("ExecutorManager is not available, cannot schedule tasks");
                return;
            }

            // 延遲啟動定期任務，確保服務器完全啟動
            executorManager.schedule(() -> {
                try {
                    schedulePeriodicLogging();
                    logger.info("Periodic tasks scheduled successfully");
                } catch (Exception e) {
                    logger.error("Error scheduling periodic tasks: {}", e.getMessage(), e);
                }
            }, 5, TimeUnit.SECONDS); // 延遲 5 秒啟動

        } catch (Exception e) {
            logger.error("Error in schedulePeriodicTasks: {}", e.getMessage(), e);
        }
    }

    /**
     * Stop the gRPC proxy server
     */
    @PreDestroy
    public void stop() {
        serverLock.lock();
        try {
            // 先取消定期任務
            if (healthCheckTask != null && !healthCheckTask.isCancelled()) {
                healthCheckTask.cancel(false);
            }
            if (statisticsTask != null && !statisticsTask.isCancelled()) {
                statisticsTask.cancel(false);
            }

            if (server != null) {
                logger.info("Stopping gRPC proxy server...");

                // 停止服務器
                server.shutdown();

                try {
                    // Wait for server to terminate gracefully
                    if (!server.awaitTermination(30, TimeUnit.SECONDS)) {
                        logger.warn("Server did not terminate gracefully within 30 seconds, forcing shutdown");
                        server.shutdownNow();

                        // Wait a bit more for forced shutdown
                        if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                            logger.error("Server did not terminate after forced shutdown");
                        }
                    }
                } catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting for server termination");
                    server.shutdownNow();
                    Thread.currentThread().interrupt();
                }

                logger.info("gRPC proxy server stopped");
                server = null;
            }
        } finally {
            serverLock.unlock();
        }
    }

    /**
     * Restart the gRPC proxy server
     */
    public void restart() throws IOException {
        logger.info("Restarting gRPC proxy server...");
        stop();
        start();
    }

    /**
     * Check if the server is running
     */
    public boolean isRunning() {
        return server != null && !server.isShutdown();
    }

    /**
     * Handle proxy configuration change events
     */
    @EventListener
    public void handleProxyConfigChangedEvent(ProxyConfigChangedEvent event) {
        logger.info("Received proxy configuration change event: " + event);

        try {
            // For any configuration change, restart the server to apply new settings
            if (isRunning()) {
                restart();
                logger.info("Server restarted successfully due to configuration change");
            } else {
                logger.warn("Server is not running, cannot restart for configuration change");
            }
        } catch (Exception e) {
            logger.error("Failed to restart server for configuration change: " + e.getMessage(), e);
        }
    }

    /**
     * Handle upstream health check events
     */
    @EventListener
    public void handleUpstreamHealthCheckEvent(UpstreamHealthCheckEvent event) {
        logger.debug("Received upstream health check event: " + event);

        // Update upstream service status
        UpstreamServiceStatus status = upstreamStatusMap.get(event.getProxyHostname());
        if (status != null) {
            status.updateHealthStatus(event.isHealthy(), event.getMessage());

            if (!event.isHealthy()) {
                logger.warn("Upstream service unhealthy: " + event.getProxyHostname() +
                        " -> " + event.getTargetHostname() + ":" + event.getTargetPort() +
                        ", message: " + event.getMessage());
            }
        }
    }

    /**
     * Create a logging interceptor for monitoring requests
     */
    private ServerInterceptor createLoggingInterceptor() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {

                // Increment total calls counter
                totalCallsProcessed.incrementAndGet();

                String methodName = call.getMethodDescriptor().getFullMethodName();
                String authority = call.getAuthority();

                logger.debug("Intercepted call: method=" + methodName + ", authority=" + authority);

                // Extract hostname for error tracking
                String hostname = authority != null ? authority.split(":")[0] : "unknown";

                // Create wrapped call for error tracking
                ServerCall<ReqT, RespT> wrappedCall = new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                    @Override
                    public void close(Status status, Metadata trailers) {
                        if (!status.isOk()) {
                            failedCallsCount.incrementAndGet();
                            errorsByHostname.computeIfAbsent(hostname, k -> new AtomicInteger(0)).incrementAndGet();

                            if (status.getCode() == Status.Code.UNAVAILABLE ||
                                    status.getCode() == Status.Code.DEADLINE_EXCEEDED) {
                                if (status.getDescription() != null &&
                                        status.getDescription().toLowerCase().contains("ssl")) {
                                    tlsErrorsCount.incrementAndGet();
                                }
                            }

                            logger.warn("Call failed: method=" + methodName +
                                    ", authority=" + authority +
                                    ", status=" + status.getCode() +
                                    ", description=" + status.getDescription());
                        }

                        super.close(status, trailers);
                    }
                };

                return next.startCall(wrappedCall, headers);
            }
        };
    }

    /**
     * Log upstream service details
     */
    private void logUpstreamServiceDetails(String action, GrpcProxyMap mapping) {
        String securityInfo = "";
        if ("SECURE".equals(mapping.getSecureMode())) {
            securityInfo = " (TLS required)";
        } else if ("PLAINTEXT".equals(mapping.getSecureMode())) {
            securityInfo = " (plaintext only)";
        } else {
            securityInfo = " (TLS auto-detect)";
        }

        logger.info(action + " upstream service: " + mapping.getProxyHostName() +
                " -> " + mapping.getTargetHostName() + ":" + mapping.getTargetPort() +
                securityInfo +
                " (timeouts: connect=" + mapping.getConnectTimeoutMs() + "ms" +
                ", send=" + mapping.getSendTimeoutMs() + "ms" +
                ", read=" + mapping.getReadTimeoutMs() + "ms)");
    }

    /**
     * Log system resources
     */
    private void logSystemResources() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        logger.info("System resources: " +
                "Max memory=" + (maxMemory / 1024 / 1024) + "MB, " +
                "Total memory=" + (totalMemory / 1024 / 1024) + "MB, " +
                "Used memory=" + (usedMemory / 1024 / 1024) + "MB, " +
                "Free memory=" + (freeMemory / 1024 / 1024) + "MB, " +
                "Available processors=" + runtime.availableProcessors());
    }

    /**
     * Schedule health checks for upstream services
     */
    private void scheduleHealthChecks() {
        if (!healthCheckEnabled) {
            logger.info("Health checks are disabled");
            return;
        }

        // 檢查執行器狀態
        if (!executorManager.isAvailable()) {
            logger.error("Cannot schedule health checks - ExecutorManager is not available");
            return;
        }

        logger.info("Scheduling health checks for {} upstream services", upstreamStatusMap.size());

        try {
            // Schedule health checks every 30 seconds - 使用 ExecutorManager
            healthCheckTask = executorManager.scheduleAtFixedRate(() -> {
                try {
                    performHealthChecks();
                } catch (Exception e) {
                    logger.error("Error during health check execution: " + e.getMessage(), e);
                }
            }, 30, 30, TimeUnit.SECONDS);

            if (healthCheckTask != null) {
                logger.info("Health checks scheduled successfully");
            } else {
                logger.warn("Failed to schedule health checks - task was null");
            }
        } catch (Exception e) {
            logger.error("Unexpected error scheduling health checks: {}", e.getMessage(), e);
        }
    }

    /**
     * Perform health checks on all upstream services
     */
    private void performHealthChecks() {
        for (UpstreamServiceStatus status : upstreamStatusMap.values()) {
            // 使用執行器提交健康檢查任務，避免阻塞主線程
            if (executorManager.isAvailable()) {
                executorManager.schedule(() -> {
                    try {
                        boolean healthy = checkUpstreamHealth(status);
                        String message = healthy ? "Health check passed" : "Health check failed - connection timeout or refused";

                        // Publish health check event
                        eventPublisher.publishEvent(new UpstreamHealthCheckEvent(
                                status.getProxyHostname(),
                                status.getTargetHostname(),
                                status.getTargetPort(),
                                healthy,
                                message
                        ));
                    } catch (Exception e) {
                        logger.error("Error checking health for " + status.getProxyHostname() + ": " + e.getMessage());

                        // Publish failed health check event
                        eventPublisher.publishEvent(new UpstreamHealthCheckEvent(
                                status.getProxyHostname(),
                                status.getTargetHostname(),
                                status.getTargetPort(),
                                false,
                                "Health check error: " + e.getMessage()
                        ));
                    }
                }, 0, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * Check health of a specific upstream service
     */
    private boolean checkUpstreamHealth(UpstreamServiceStatus status) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(status.getTargetHostname(), status.getTargetPort()), 5000);
            return true;
        } catch (SocketTimeoutException e) {
            logger.debug("Health check timeout for " + status.getProxyHostname());
            return false;
        } catch (IOException e) {
            logger.debug("Health check failed for " + status.getProxyHostname() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Schedule periodic statistics logging
     */
    private void schedulePeriodicLogging() {
        if (!executorManager.isAvailable()) {
            logger.warn("Cannot schedule periodic logging - ExecutorManager is not available");
            return;
        }

        try {
            statisticsTask = executorManager.scheduleAtFixedRate(() -> {
                try {
                    logStatistics();
                } catch (Exception e) {
                    logger.error("Error logging statistics: " + e.getMessage(), e);
                }
            }, 60, 60, TimeUnit.SECONDS);

            if (statisticsTask != null) {
                logger.debug("Periodic logging scheduled successfully");
            } else {
                logger.warn("Failed to schedule periodic logging - task was null");
            }
        } catch (Exception e) {
            logger.error("Unexpected error scheduling periodic logging: {}", e.getMessage(), e);
        }
    }

    /**
     * Log server statistics
     */
    private void logStatistics() {
        int totalCalls = totalCallsProcessed.get();
        int failedCalls = failedCallsCount.get();
        int tlsErrors = tlsErrorsCount.get();

        if (totalCalls > 0) {
            double errorRate = (double) failedCalls / totalCalls * 100;

            logger.info("Server statistics: " +
                    "Total calls=" + totalCalls +
                    ", Failed calls=" + failedCalls +
                    " (" + String.format("%.2f", errorRate) + "%), " +
                    "TLS errors=" + tlsErrors +
                    ", Active upstream services=" + upstreamStatusMap.size());

            // Log error breakdown by hostname if there are errors
            if (failedCalls > 0 && !errorsByHostname.isEmpty()) {
                logger.info("Error breakdown by hostname:");
                errorsByHostname.forEach((hostname, count) ->
                        logger.info("  " + hostname + ": " + count.get() + " errors"));
            }
        }
    }

    /**
     * Get server statistics
     */
    public ServerStatistics getStatistics() {
        return new ServerStatistics(
                totalCallsProcessed.get(),
                failedCallsCount.get(),
                tlsErrorsCount.get(),
                upstreamStatusMap.size(),
                isRunning()
        );
    }

    /**
     * Upstream service status tracking
     */
    private static class UpstreamServiceStatus {
        private final String proxyHostname;
        private final String targetHostname;
        private final int targetPort;
        private final String secureMode;
        private volatile boolean healthy = true;
        private volatile String lastHealthMessage = "Unknown";
        private volatile long lastHealthCheckTime = System.currentTimeMillis();

        public UpstreamServiceStatus(String proxyHostname, String targetHostname, int targetPort, String secureMode) {
            this.proxyHostname = proxyHostname;
            this.targetHostname = targetHostname;
            this.targetPort = targetPort;
            this.secureMode = secureMode;
        }

        public void updateHealthStatus(boolean healthy, String message) {
            this.healthy = healthy;
            this.lastHealthMessage = message;
            this.lastHealthCheckTime = System.currentTimeMillis();
        }

        // Getters
        public String getProxyHostname() { return proxyHostname; }
        public String getTargetHostname() { return targetHostname; }
        public int getTargetPort() { return targetPort; }
        public String getSecureMode() { return secureMode; }
        public boolean isHealthy() { return healthy; }
        public String getLastHealthMessage() { return lastHealthMessage; }
        public long getLastHealthCheckTime() { return lastHealthCheckTime; }
    }

    /**
     * Server statistics data class
     */
    public static class ServerStatistics {
        private final int totalCalls;
        private final int failedCalls;
        private final int tlsErrors;
        private final int upstreamServicesCount;
        private final boolean running;

        public ServerStatistics(int totalCalls, int failedCalls, int tlsErrors, int upstreamServicesCount, boolean running) {
            this.totalCalls = totalCalls;
            this.failedCalls = failedCalls;
            this.tlsErrors = tlsErrors;
            this.upstreamServicesCount = upstreamServicesCount;
            this.running = running;
        }

        // Getters
        public int getTotalCalls() { return totalCalls; }
        public int getFailedCalls() { return failedCalls; }
        public int getTlsErrors() { return tlsErrors; }
        public int getUpstreamServicesCount() { return upstreamServicesCount; }
        public boolean isRunning() { return running; }
        public double getErrorRate() { return totalCalls > 0 ? (double) failedCalls / totalCalls * 100 : 0; }
    }
}