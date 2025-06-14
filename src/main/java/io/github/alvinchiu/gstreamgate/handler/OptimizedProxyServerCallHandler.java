package io.github.alvinchiu.gstreamgate.handler;

import io.github.alvinchiu.gstreamgate.adaptive.AdaptiveTimeoutManager;
import io.github.alvinchiu.gstreamgate.adaptive.SmartFlowControlManager;
import io.github.alvinchiu.gstreamgate.circuit.CircuitBreakerManager;
import io.github.alvinchiu.gstreamgate.marshaller.PassthroughMarshaller;
import io.github.alvinchiu.gstreamgate.metrics.ProxyMetrics;
import io.github.alvinchiu.gstreamgate.optimization.MemoryOptimizer;
import io.github.alvinchiu.gstreamgate.tracing.TracingUtils;
import io.grpc.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Optimized gRPC proxy call handler - fixes ByteBuf API issues
 * Combines connection pooling, circuit breaker, metrics collection,
 * memory optimization and adaptive control
 */
public class OptimizedProxyServerCallHandler implements ServerCallHandler<InputStream, InputStream> {
    private static final Logger logger = LoggerFactory.getLogger(OptimizedProxyServerCallHandler.class);

    private final ManagedChannel channel;
    private final String fullMethodName;
    private final String targetKey;
    private static final AtomicLong callCounter = new AtomicLong(0);

    // Injected optimization components
    private static CircuitBreakerManager circuitBreakerManager;
    private static ProxyMetrics proxyMetrics;
    private static MemoryOptimizer memoryOptimizer;
    private static AdaptiveTimeoutManager timeoutManager;
    private static SmartFlowControlManager flowControlManager;
    private static TracingUtils tracingUtils;

    // Default timeout setting
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    /**
     * Inject optimization components statically
     */
    public static void injectOptimizedComponents(
            CircuitBreakerManager circuitBreaker,
            ProxyMetrics metrics,
            MemoryOptimizer memoryOpt,
            AdaptiveTimeoutManager timeoutMgr,
            SmartFlowControlManager flowControlMgr,
            TracingUtils tracing) {

        circuitBreakerManager = circuitBreaker;
        proxyMetrics = metrics;
        memoryOptimizer = memoryOpt;
        timeoutManager = timeoutMgr;
        flowControlManager = flowControlMgr;
        tracingUtils = tracing;

        logger.info("Optimized components injected into ProxyServerCallHandler with tracing support");
    }

    public OptimizedProxyServerCallHandler(ManagedChannel channel, String fullMethodName, String targetKey) {
        this.channel = channel;
        this.fullMethodName = fullMethodName;
        this.targetKey = targetKey;
        logger.debug("Created OptimizedProxyServerCallHandler for method: {} -> {}", fullMethodName, targetKey);
    }

    @Override
    public ServerCall.Listener<InputStream> startCall(
            ServerCall<InputStream, InputStream> serverCall,
            Metadata headers) {

        String callId = generateCallId();
        Instant startTime = Instant.now();

        logger.debug("[{}] Starting optimized call for method: {}", callId, fullMethodName);

        try {
            // Check circuit breaker state
            if (circuitBreakerManager != null) {
                try {
                    // Invoke within circuit breaker protection
                    return circuitBreakerManager.execute(targetKey, () ->
                            createOptimizedCall(serverCall, headers, callId, startTime));
                } catch (CircuitBreakerManager.CircuitBreakerOpenException e) {
                    logger.warn("[{}] Circuit breaker is open for target: {}", callId, targetKey);

                    // Record circuit breaker open errors
                    if (proxyMetrics != null) {
                        proxyMetrics.recordError(targetKey, "CIRCUIT_BREAKER_OPEN", e.getMessage());
                    }

                    // Return error to client
                    serverCall.close(Status.UNAVAILABLE.withDescription("Service temporarily unavailable"),
                            new Metadata());
                    return new ServerCall.Listener<InputStream>() {};
                }
            } else {
                // Fallback logic when no circuit breaker
                return createOptimizedCall(serverCall, headers, callId, startTime);
            }

        } catch (Exception e) {
            logger.error("[{}] Error starting optimized call: {}", callId, e.getMessage(), e);

            // Record error
            if (proxyMetrics != null) {
                Duration duration = Duration.between(startTime, Instant.now());
                proxyMetrics.recordRequest(fullMethodName, targetKey, duration, false, 0);
                proxyMetrics.recordError(targetKey, "CALL_START_ERROR", e.getMessage());
            }

            // Return error
            serverCall.close(Status.INTERNAL.withDescription("Internal server error"), new Metadata());
            return new ServerCall.Listener<InputStream>() {};
        }
    }

    /**
     * Create optimized call
     */
    private ServerCall.Listener<InputStream> createOptimizedCall(
            ServerCall<InputStream, InputStream> serverCall,
            Metadata headers,
            String callId,
            Instant startTime) {

        // Initialize adaptive components
        if (timeoutManager != null) {
            timeoutManager.startCall(fullMethodName, callId);
        }
        if (flowControlManager != null) {
            flowControlManager.initializeFlowControl(callId);
        }

        // Get adaptive timeout
        int timeoutSeconds = getAdaptiveTimeout();

        // Create client call
        ClientCall<InputStream, InputStream> clientCall = channel.newCall(
                MethodDescriptor.<InputStream, InputStream>newBuilder()
                        .setType(MethodDescriptor.MethodType.UNKNOWN)
                        .setFullMethodName(fullMethodName)
                        .setRequestMarshaller(new PassthroughMarshaller())
                        .setResponseMarshaller(new PassthroughMarshaller())
                        .build(),
                CallOptions.DEFAULT
                        .withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS)
                        .withMaxInboundMessageSize(20 * 1024 * 1024)
                        .withMaxOutboundMessageSize(20 * 1024 * 1024));

        // Determine if call is likely streaming
        boolean isLikelyStreaming = isLikelyStreamingMethod(fullMethodName);

        // Create optimized client listener
        OptimizedClientCallListener clientListener = new OptimizedClientCallListener(
                clientCall, serverCall, callId, targetKey, startTime, isLikelyStreaming);

        // Start client call
        clientCall.start(clientListener, headers);

        // Apply initial flow control
        if (flowControlManager != null) {
            flowControlManager.applyFlowControl(clientCall, callId, isLikelyStreaming);
        } else {
            clientCall.request(isLikelyStreaming ? 2 : 1);
        }

        // Create optimized server listener
        OptimizedServerCallListener serverListener = new OptimizedServerCallListener(
                clientCall, serverCall, callId, targetKey, startTime, isLikelyStreaming);

        // Apply server-side flow control
        if (flowControlManager != null) {
            flowControlManager.applyFlowControl(serverCall, callId, isLikelyStreaming);
        } else {
            serverCall.request(isLikelyStreaming ? 2 : 1);
        }

        return serverListener;
    }

    /**
     * Generate unique call ID
     */
    private String generateCallId() {
        String methodShortName = fullMethodName;
        int lastSlashIndex = fullMethodName.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < fullMethodName.length() - 1) {
            methodShortName = fullMethodName.substring(lastSlashIndex + 1);
        }
        return methodShortName + "-" + callCounter.incrementAndGet() + "-" +
                Long.toHexString(System.nanoTime() & 0xFFFF);
    }

    /**
     * Get adaptive timeout
     */
    private int getAdaptiveTimeout() {
        if (timeoutManager != null) {
            int adaptiveTimeout = timeoutManager.getTimeout(fullMethodName);
            if (adaptiveTimeout > 0) {
                return adaptiveTimeout;
            }
        }
        return DEFAULT_TIMEOUT_SECONDS;
    }

    /**
     * Determine if method is streaming
     */
    private boolean isLikelyStreamingMethod(String methodName) {
        String lowerName = methodName.toLowerCase();
        return lowerName.contains("stream") ||
                lowerName.contains("watch") ||
                lowerName.contains("observe") ||
                lowerName.contains("monitor") ||
                lowerName.contains("subscribe") ||
                lowerName.contains("list") ||
                lowerName.contains("chat") ||
                lowerName.contains("continuous");
    }

    /**
     * Helper to convert ByteBuf to InputStream
     */
    private static InputStream byteBufToInputStream(ByteBuf byteBuf) {
        if (byteBuf.hasArray()) {
            // Directly use if ByteBuf has backing array
            return new ByteArrayInputStream(
                    byteBuf.array(),
                    byteBuf.arrayOffset() + byteBuf.readerIndex(),
                    byteBuf.readableBytes()
            );
        } else {
            // Use Netty provided ByteBufInputStream
            return new ByteBufInputStream(byteBuf, byteBuf.readableBytes());
        }
    }


    /**
     * Optimized client call listener
     */
    private class OptimizedClientCallListener extends ClientCall.Listener<InputStream> {
        private final ClientCall<InputStream, InputStream> clientCall;
        private final ServerCall<InputStream, InputStream> serverCall;
        private final String callId;
        private final String targetKey;
        private final Instant startTime;
        private final boolean isStreaming;

        private boolean headersSent = false;
        private int messageCount = 0;
        private long totalBytesReceived = 0;

        public OptimizedClientCallListener(ClientCall<InputStream, InputStream> clientCall,
                                           ServerCall<InputStream, InputStream> serverCall,
                                           String callId, String targetKey, Instant startTime, boolean isStreaming) {
            this.clientCall = clientCall;
            this.serverCall = serverCall;
            this.callId = callId;
            this.targetKey = targetKey;
            this.startTime = startTime;
            this.isStreaming = isStreaming;
        }

        @Override
        public void onMessage(InputStream message) {
            messageCount++;

            try {
                // Handle message with memory optimizer
                ByteBuf messageBuf = null;
                InputStream processedMessage = message;

                // Use zero-copy approach - avoid blocking I/O operations
                // PassthroughMarshaller now provides direct stream access
                processedMessage = message;
                
                // Estimate message size for metrics without blocking reads
                if (message.markSupported()) {
                    try {
                        message.mark(Integer.MAX_VALUE);
                        totalBytesReceived += message.available();
                        message.reset();
                    } catch (Exception e) {
                        logger.debug("[{}] Could not estimate message size: {}", callId, e.getMessage());
                    }
                }

                // Record message receipt
                if (timeoutManager != null) {
                    timeoutManager.recordMessage(callId);
                }
                if (flowControlManager != null) {
                    flowControlManager.startProcessingMessage(callId);
                }

                // Send headers if not yet sent
                if (!headersSent) {
                    serverCall.sendHeaders(new Metadata());
                    headersSent = true;
                }

                // Forward message
                serverCall.sendMessage(processedMessage);

                logger.debug("[{}] Forwarded message #{} ({} bytes)", callId, messageCount,
                        messageBuf != null ? messageBuf.readableBytes() : "unknown");

                // Finish message processing
                if (flowControlManager != null) {
                    flowControlManager.completeProcessingMessage(callId);
                    flowControlManager.applyFlowControl(serverCall, callId, isStreaming);
                } else {
                    serverCall.request(1);
                }

                // Release buffer
                if (messageBuf != null && memoryOptimizer != null) {
                    memoryOptimizer.releaseByteBuf(messageBuf);
                }

            } catch (Exception e) {
                logger.error("[{}] Error processing message: {}", callId, e.getMessage(), e);

                // Record error
                if (proxyMetrics != null) {
                    proxyMetrics.recordError(targetKey, "MESSAGE_PROCESSING_ERROR", e.getMessage());
                }

                // Close call
                serverCall.close(Status.INTERNAL.withDescription("Message processing error"), new Metadata());

                throw new RuntimeException("Message processing failed", e);
            }
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            Duration callDuration = Duration.between(startTime, Instant.now());

            try {
                // Send headers if not yet sent
                if (!headersSent) {
                    serverCall.sendHeaders(new Metadata());
                    headersSent = true;
                }

                // Close server call
                serverCall.close(status, trailers);

                // Record call completion
                boolean success = status.isOk();

                if (success) {
                    logger.debug("[{}] Call completed successfully in {}ms, {} messages, {} bytes",
                            callId, callDuration.toMillis(), messageCount, totalBytesReceived);
                } else {
                    logger.warn("[{}] Call failed with status: {} in {}ms",
                            callId, status.getCode(), callDuration.toMillis());
                }

                // Record metrics
                if (proxyMetrics != null) {
                    proxyMetrics.recordRequest(fullMethodName, targetKey, callDuration, success, totalBytesReceived);
                    if (totalBytesReceived > 0) {
                        proxyMetrics.recordTraffic(targetKey, totalBytesReceived, 0);
                    }
                }

                // Complete adaptive component tracking
                if (timeoutManager != null) {
                    timeoutManager.completeCall(fullMethodName, callId);
                }
                if (flowControlManager != null) {
                    flowControlManager.cleanupFlowControl(callId);
                }

            } catch (Exception e) {
                logger.error("[{}] Error during call close: {}", callId, e.getMessage(), e);

                if (proxyMetrics != null) {
                    proxyMetrics.recordError(targetKey, "CALL_CLOSE_ERROR", e.getMessage());
                }
            }
        }

        @Override
        public void onReady() {
            logger.debug("[{}] Client call ready", callId);
        }
    }

    /**
     * Optimized server call listener
     */
    private class OptimizedServerCallListener extends ServerCall.Listener<InputStream> {
        private final ClientCall<InputStream, InputStream> clientCall;
        private final ServerCall<InputStream, InputStream> serverCall;
        private final String callId;
        private final String targetKey;
        private final Instant startTime;
        private final boolean isStreaming;

        private int messageCount = 0;
        private long totalBytesSent = 0;
        private boolean halfCloseSent = false;

        public OptimizedServerCallListener(ClientCall<InputStream, InputStream> clientCall,
                                           ServerCall<InputStream, InputStream> serverCall,
                                           String callId, String targetKey, Instant startTime, boolean isStreaming) {
            this.clientCall = clientCall;
            this.serverCall = serverCall;
            this.callId = callId;
            this.targetKey = targetKey;
            this.startTime = startTime;
            this.isStreaming = isStreaming;
        }

        @Override
        public void onMessage(InputStream message) {
            messageCount++;

            try {
                // Handle message with memory optimizer
                ByteBuf messageBuf = null;
                InputStream processedMessage = message;

                // Use zero-copy approach - avoid blocking I/O operations
                // PassthroughMarshaller now provides direct stream access
                processedMessage = message;
                
                // Estimate message size for metrics without blocking reads
                if (message.markSupported()) {
                    try {
                        message.mark(Integer.MAX_VALUE);
                        totalBytesSent += message.available();
                        message.reset();
                    } catch (Exception e) {
                        logger.debug("[{}] Could not estimate message size: {}", callId, e.getMessage());
                    }
                }

                // Forward message upstream
                clientCall.sendMessage(processedMessage);

                logger.debug("[{}] Sent message #{} to upstream ({} bytes)", callId, messageCount,
                        messageBuf != null ? messageBuf.readableBytes() : "unknown");

                // Record message handling
                if (timeoutManager != null) {
                    timeoutManager.recordMessage(callId);
                }
                if (flowControlManager != null) {
                    flowControlManager.startProcessingMessage(callId);
                    flowControlManager.completeProcessingMessage(callId);
                    flowControlManager.applyFlowControl(clientCall, callId, isStreaming);
                } else {
                    clientCall.request(1);
                }

                // Request more messages
                serverCall.request(1);

                // Release buffer
                if (messageBuf != null && memoryOptimizer != null) {
                    memoryOptimizer.releaseByteBuf(messageBuf);
                }

            } catch (Exception e) {
                logger.error("[{}] Error forwarding message to upstream: {}", callId, e.getMessage(), e);

                // Record error
                if (proxyMetrics != null) {
                    proxyMetrics.recordError(targetKey, "MESSAGE_FORWARD_ERROR", e.getMessage());
                }

                // Cancel call
                clientCall.cancel("Message forwarding error", e);
                serverCall.close(Status.INTERNAL.withDescription("Message forwarding error"), new Metadata());
            }
        }

        @Override
        public void onHalfClose() {
            if (halfCloseSent) {
                return;
            }
            halfCloseSent = true;

            try {
                logger.debug("[{}] Half-closing upstream call after {} messages", callId, messageCount);

                // Remove blocking Thread.sleep() - use asynchronous approach
                // The flow control manager will handle proper message sequencing
                clientCall.halfClose();
            } catch (Exception e) {
                logger.error("[{}] Error during half-close: {}", callId, e.getMessage(), e);
                clientCall.cancel("Half-close error", e);
            }
        }

        @Override
        public void onCancel() {
            logger.debug("[{}] Server call cancelled after {} messages", callId, messageCount);

            try {
                clientCall.cancel("Client cancelled", null);

                // Record cancel event
                if (proxyMetrics != null) {
                    Duration callDuration = Duration.between(startTime, Instant.now());
                    proxyMetrics.recordRequest(fullMethodName, targetKey, callDuration, false, totalBytesSent);
                    if (totalBytesSent > 0) {
                        proxyMetrics.recordTraffic(targetKey, 0, totalBytesSent);
                    }
                }

                // Clean up adaptive components
                if (timeoutManager != null) {
                    timeoutManager.completeCall(fullMethodName, callId);
                }
                if (flowControlManager != null) {
                    flowControlManager.cleanupFlowControl(callId);
                }

            } catch (Exception e) {
                logger.error("[{}] Error during cancel: {}", callId, e.getMessage(), e);
            }
        }

        @Override
        public void onComplete() {
            Duration callDuration = Duration.between(startTime, Instant.now());
            logger.debug("[{}] Server call completed after {} messages in {}ms",
                    callId, messageCount, callDuration.toMillis());

            try {
                // Record completion event
                if (proxyMetrics != null && totalBytesSent > 0) {
                    proxyMetrics.recordTraffic(targetKey, 0, totalBytesSent);
                }

                // Clean up adaptive components
                if (timeoutManager != null) {
                    timeoutManager.completeCall(fullMethodName, callId);
                }
                if (flowControlManager != null) {
                    flowControlManager.cleanupFlowControl(callId);
                }

            } catch (Exception e) {
                logger.error("[{}] Error during completion: {}", callId, e.getMessage(), e);
            }
        }

        @Override
        public void onReady() {
            logger.debug("[{}] Server call ready", callId);

            // Apply flow control
            if (flowControlManager != null) {
                try {
                    flowControlManager.applyFlowControl(clientCall, callId, isStreaming);
                } catch (Exception e) {
                    logger.warn("[{}] Error applying flow control on ready: {}", callId, e.getMessage());
                }
            }
        }
    }
}