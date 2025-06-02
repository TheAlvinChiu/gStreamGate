package io.github.alvinchiu.gstreamgate.handler;

import io.github.alvinchiu.gstreamgate.adaptive.AdaptiveTimeoutManager;
import io.github.alvinchiu.gstreamgate.adaptive.SmartFlowControlManager;
import io.github.alvinchiu.gstreamgate.circuit.CircuitBreakerManager;
import io.github.alvinchiu.gstreamgate.marshaller.PassthroughMarshaller;
import io.github.alvinchiu.gstreamgate.metrics.ProxyMetrics;
import io.github.alvinchiu.gstreamgate.optimization.MemoryOptimizer;
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
 * 優化版 gRPC 代理服務器調用處理器 - 修復 ByteBuf API 問題
 * 整合了連接池、熔斷器、Metrics 收集、內存優化和自適應控制
 */
public class OptimizedProxyServerCallHandler implements ServerCallHandler<InputStream, InputStream> {
    private static final Logger logger = LoggerFactory.getLogger(OptimizedProxyServerCallHandler.class);

    private final ManagedChannel channel;
    private final String fullMethodName;
    private final String targetKey;
    private static final AtomicLong callCounter = new AtomicLong(0);

    // 注入的優化組件
    private static CircuitBreakerManager circuitBreakerManager;
    private static ProxyMetrics proxyMetrics;
    private static MemoryOptimizer memoryOptimizer;
    private static AdaptiveTimeoutManager timeoutManager;
    private static SmartFlowControlManager flowControlManager;

    // 默認超時設置
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    /**
     * 靜態方法注入優化組件
     */
    public static void injectOptimizedComponents(
            CircuitBreakerManager circuitBreaker,
            ProxyMetrics metrics,
            MemoryOptimizer memoryOpt,
            AdaptiveTimeoutManager timeoutMgr,
            SmartFlowControlManager flowControlMgr) {

        circuitBreakerManager = circuitBreaker;
        proxyMetrics = metrics;
        memoryOptimizer = memoryOpt;
        timeoutManager = timeoutMgr;
        flowControlManager = flowControlMgr;

        logger.info("Optimized components injected into ProxyServerCallHandler");
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
            // 檢查熔斷器狀態
            if (circuitBreakerManager != null) {
                try {
                    // 使用熔斷器保護調用
                    return circuitBreakerManager.execute(targetKey, () ->
                            createOptimizedCall(serverCall, headers, callId, startTime));
                } catch (CircuitBreakerManager.CircuitBreakerOpenException e) {
                    logger.warn("[{}] Circuit breaker is open for target: {}", callId, targetKey);

                    // 記錄熔斷器打開的錯誤
                    if (proxyMetrics != null) {
                        proxyMetrics.recordError(targetKey, "CIRCUIT_BREAKER_OPEN", e.getMessage());
                    }

                    // 返回錯誤給客戶端
                    serverCall.close(Status.UNAVAILABLE.withDescription("Service temporarily unavailable"),
                            new Metadata());
                    return new ServerCall.Listener<InputStream>() {};
                }
            } else {
                // 沒有熔斷器時的降級處理
                return createOptimizedCall(serverCall, headers, callId, startTime);
            }

        } catch (Exception e) {
            logger.error("[{}] Error starting optimized call: {}", callId, e.getMessage(), e);

            // 記錄錯誤
            if (proxyMetrics != null) {
                Duration duration = Duration.between(startTime, Instant.now());
                proxyMetrics.recordRequest(fullMethodName, targetKey, duration, false, 0);
                proxyMetrics.recordError(targetKey, "CALL_START_ERROR", e.getMessage());
            }

            // 返回錯誤
            serverCall.close(Status.INTERNAL.withDescription("Internal server error"), new Metadata());
            return new ServerCall.Listener<InputStream>() {};
        }
    }

    /**
     * 創建優化的調用
     */
    private ServerCall.Listener<InputStream> createOptimizedCall(
            ServerCall<InputStream, InputStream> serverCall,
            Metadata headers,
            String callId,
            Instant startTime) {

        // 初始化自適應組件
        if (timeoutManager != null) {
            timeoutManager.startCall(fullMethodName, callId);
        }
        if (flowControlManager != null) {
            flowControlManager.initializeFlowControl(callId);
        }

        // 獲取自適應超時
        int timeoutSeconds = getAdaptiveTimeout();

        // 創建客戶端調用
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

        // 判斷是否為流式調用
        boolean isLikelyStreaming = isLikelyStreamingMethod(fullMethodName);

        // 創建優化的客戶端監聽器
        OptimizedClientCallListener clientListener = new OptimizedClientCallListener(
                clientCall, serverCall, callId, targetKey, startTime, isLikelyStreaming);

        // 啟動客戶端調用
        clientCall.start(clientListener, headers);

        // 設置初始流量控制
        if (flowControlManager != null) {
            flowControlManager.applyFlowControl(clientCall, callId, isLikelyStreaming);
        } else {
            clientCall.request(isLikelyStreaming ? 2 : 1);
        }

        // 創建優化的服務器監聽器
        OptimizedServerCallListener serverListener = new OptimizedServerCallListener(
                clientCall, serverCall, callId, targetKey, startTime, isLikelyStreaming);

        // 設置服務器端流量控制
        if (flowControlManager != null) {
            flowControlManager.applyFlowControl(serverCall, callId, isLikelyStreaming);
        } else {
            serverCall.request(isLikelyStreaming ? 2 : 1);
        }

        return serverListener;
    }

    /**
     * 生成唯一調用 ID
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
     * 獲取自適應超時時間
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
     * 判斷是否為流式方法
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
     * 輔助方法：將 ByteBuf 轉換為 InputStream
     */
    private static InputStream byteBufToInputStream(ByteBuf byteBuf) {
        if (byteBuf.hasArray()) {
            // 如果 ByteBuf 有底層數組，直接使用
            return new ByteArrayInputStream(
                    byteBuf.array(),
                    byteBuf.arrayOffset() + byteBuf.readerIndex(),
                    byteBuf.readableBytes()
            );
        } else {
            // 使用 Netty 提供的 ByteBufInputStream
            return new ByteBufInputStream(byteBuf, byteBuf.readableBytes());
        }
    }

    /**
     * 輔助方法：安全地讀取 InputStream 到字節數組
     */
    private static byte[] readInputStreamToBytes(InputStream inputStream) throws Exception {
        if (inputStream instanceof ByteArrayInputStream) {
            // 如果已經是 ByteArrayInputStream，直接讀取
            return inputStream.readAllBytes();
        } else {
            // 使用 ByteArrayOutputStream 來緩衝
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] temp = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(temp)) != -1) {
                buffer.write(temp, 0, bytesRead);
            }
            return buffer.toByteArray();
        }
    }

    /**
     * 優化的客戶端調用監聽器
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
                // 使用內存優化器處理消息
                ByteBuf messageBuf = null;
                InputStream processedMessage = message;

                if (memoryOptimizer != null) {
                    // 讀取消息到字節數組
                    byte[] messageBytes = readInputStreamToBytes(message);
                    totalBytesReceived += messageBytes.length;

                    // 使用零拷貝 ByteBuf
                    messageBuf = memoryOptimizer.createZeroCopyByteBuf(messageBytes);

                    // 轉換回 InputStream 發送 - 修復 API 調用
                    processedMessage = byteBufToInputStream(messageBuf);
                } else {
                    // 沒有內存優化器時，估算字節數
                    try {
                        byte[] bytes = readInputStreamToBytes(message);
                        totalBytesReceived += bytes.length;
                        processedMessage = new ByteArrayInputStream(bytes);
                    } catch (Exception e) {
                        logger.warn("[{}] Failed to read message bytes for size calculation", callId);
                    }
                }

                // 記錄消息接收
                if (timeoutManager != null) {
                    timeoutManager.recordMessage(callId);
                }
                if (flowControlManager != null) {
                    flowControlManager.startProcessingMessage(callId);
                }

                // 發送頭部（如果尚未發送）
                if (!headersSent) {
                    serverCall.sendHeaders(new Metadata());
                    headersSent = true;
                }

                // 轉發消息
                serverCall.sendMessage(processedMessage);

                logger.debug("[{}] Forwarded message #{} ({} bytes)", callId, messageCount,
                        messageBuf != null ? messageBuf.readableBytes() : "unknown");

                // 完成消息處理
                if (flowControlManager != null) {
                    flowControlManager.completeProcessingMessage(callId);
                    flowControlManager.applyFlowControl(serverCall, callId, isStreaming);
                } else {
                    serverCall.request(1);
                }

                // 釋放緩衝區
                if (messageBuf != null && memoryOptimizer != null) {
                    memoryOptimizer.releaseByteBuf(messageBuf);
                }

            } catch (Exception e) {
                logger.error("[{}] Error processing message: {}", callId, e.getMessage(), e);

                // 記錄錯誤
                if (proxyMetrics != null) {
                    proxyMetrics.recordError(targetKey, "MESSAGE_PROCESSING_ERROR", e.getMessage());
                }

                // 關閉調用
                serverCall.close(Status.INTERNAL.withDescription("Message processing error"), new Metadata());

                throw new RuntimeException("Message processing failed", e);
            }
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            Duration callDuration = Duration.between(startTime, Instant.now());

            try {
                // 發送頭部（如果尚未發送）
                if (!headersSent) {
                    serverCall.sendHeaders(new Metadata());
                    headersSent = true;
                }

                // 關閉服務器調用
                serverCall.close(status, trailers);

                // 記錄調用完成
                boolean success = status.isOk();

                if (success) {
                    logger.debug("[{}] Call completed successfully in {}ms, {} messages, {} bytes",
                            callId, callDuration.toMillis(), messageCount, totalBytesReceived);
                } else {
                    logger.warn("[{}] Call failed with status: {} in {}ms",
                            callId, status.getCode(), callDuration.toMillis());
                }

                // 記錄 Metrics
                if (proxyMetrics != null) {
                    proxyMetrics.recordRequest(fullMethodName, targetKey, callDuration, success, totalBytesReceived);
                    if (totalBytesReceived > 0) {
                        proxyMetrics.recordTraffic(targetKey, totalBytesReceived, 0);
                    }
                }

                // 完成自適應組件跟蹤
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
     * 優化的服務器調用監聽器
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
                // 使用內存優化器處理消息
                ByteBuf messageBuf = null;
                InputStream processedMessage = message;

                if (memoryOptimizer != null) {
                    byte[] messageBytes = readInputStreamToBytes(message);
                    totalBytesSent += messageBytes.length;

                    // 使用零拷貝 ByteBuf
                    messageBuf = memoryOptimizer.createZeroCopyByteBuf(messageBytes);
                    processedMessage = byteBufToInputStream(messageBuf);
                } else {
                    // 沒有內存優化器時，估算字節數
                    try {
                        byte[] bytes = readInputStreamToBytes(message);
                        totalBytesSent += bytes.length;
                        processedMessage = new ByteArrayInputStream(bytes);
                    } catch (Exception e) {
                        logger.warn("[{}] Failed to read message bytes for size calculation", callId);
                    }
                }

                // 轉發消息到上游
                clientCall.sendMessage(processedMessage);

                logger.debug("[{}] Sent message #{} to upstream ({} bytes)", callId, messageCount,
                        messageBuf != null ? messageBuf.readableBytes() : "unknown");

                // 記錄消息處理
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

                // 請求更多消息
                serverCall.request(1);

                // 釋放緩衝區
                if (messageBuf != null && memoryOptimizer != null) {
                    memoryOptimizer.releaseByteBuf(messageBuf);
                }

            } catch (Exception e) {
                logger.error("[{}] Error forwarding message to upstream: {}", callId, e.getMessage(), e);

                // 記錄錯誤
                if (proxyMetrics != null) {
                    proxyMetrics.recordError(targetKey, "MESSAGE_FORWARD_ERROR", e.getMessage());
                }

                // 取消調用
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

                // 添加適當的延遲以確保所有消息都已處理
                if (messageCount > 1) {
                    Thread.sleep(100); // 短暫延遲
                }

                clientCall.halfClose();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("[{}] Interrupted during half-close", callId);
                clientCall.cancel("Interrupted during half-close", e);
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

                // 記錄取消事件
                if (proxyMetrics != null) {
                    Duration callDuration = Duration.between(startTime, Instant.now());
                    proxyMetrics.recordRequest(fullMethodName, targetKey, callDuration, false, totalBytesSent);
                    if (totalBytesSent > 0) {
                        proxyMetrics.recordTraffic(targetKey, 0, totalBytesSent);
                    }
                }

                // 清理自適應組件
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
                // 記錄完成事件
                if (proxyMetrics != null && totalBytesSent > 0) {
                    proxyMetrics.recordTraffic(targetKey, 0, totalBytesSent);
                }

                // 清理自適應組件
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

            // 應用流量控制
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