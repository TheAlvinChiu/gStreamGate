package io.github.alvinchiu.gstreamgate.tracing;

import io.github.alvinchiu.gstreamgate.service.GrpcCallLogService;
import io.grpc.*;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * gRPC 呼叫記錄攔截器
 * 專門用於記錄 gRPC 呼叫資訊到資料庫
 */
@Component
public class GrpcCallLoggingInterceptor implements ServerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(GrpcCallLoggingInterceptor.class);
    
    private final GrpcCallLogService grpcCallLogService;
    
    @Autowired
    public GrpcCallLoggingInterceptor(GrpcCallLogService grpcCallLogService) {
        this.grpcCallLogService = grpcCallLogService;
        logger.info("gRPC call logging interceptor initialized");
    }
    
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        
        String fullMethodName = call.getMethodDescriptor().getFullMethodName();
        String clientIp = extractClientIp(call);
        String targetLocation = extractTargetLocation(call);
        String callType = getCallType(call.getMethodDescriptor());
        
        LocalDateTime callStartTime = LocalDateTime.now();
        
        // 建立 request/response size 追蹤
        AtomicLong requestSize = new AtomicLong(0);
        AtomicLong responseSize = new AtomicLong(0);
        AtomicReference<Status> finalStatus = new AtomicReference<>();
        AtomicReference<String> errorMessage = new AtomicReference<>();
        
        logger.debug("gRPC call started: {} from {} to {}", fullMethodName, clientIp, targetLocation);
        
        // 建立包裝的 ServerCall 來追蹤響應
        ServerCall<ReqT, RespT> wrappedCall = new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            @Override
            public void sendMessage(RespT message) {
                try {
                    // 嘗試估算響應大小
                    if (message != null) {
                        responseSize.addAndGet(estimateMessageSize(message));
                    }
                } catch (Exception e) {
                    logger.debug("Could not estimate response message size: {}", e.getMessage());
                }
                super.sendMessage(message);
            }
            
            @Override
            public void close(Status status, Metadata trailers) {
                try {
                    finalStatus.set(status);
                    if (!status.isOk() && status.getDescription() != null) {
                        errorMessage.set(status.getDescription());
                    }
                    
                    // 記錄呼叫結束
                    LocalDateTime callEndTime = LocalDateTime.now();
                    long executionTimeMs = java.time.Duration.between(callStartTime, callEndTime).toMillis();
                    
                    // 獲取追蹤資訊
                    String traceId = null;
                    String spanId = null;
                    try {
                        Span currentSpan = Span.current();
                        SpanContext spanContext = currentSpan.getSpanContext();
                        if (spanContext.isValid()) {
                            traceId = spanContext.getTraceId();
                            spanId = spanContext.getSpanId();
                        }
                    } catch (Exception e) {
                        logger.debug("Could not extract trace information: {}", e.getMessage());
                    }
                    
                    // 異步記錄呼叫資訊
                    grpcCallLogService.logCallWithDetailsAsync(
                        clientIp,
                        targetLocation,
                        fullMethodName,
                        executionTimeMs,
                        status.getCode().toString(),
                        callStartTime,
                        callEndTime,
                        callType,
                        requestSize.get(),
                        responseSize.get(),
                        errorMessage.get(),
                        traceId,
                        spanId
                    );
                    
                } catch (Exception e) {
                    logger.error("Error logging gRPC call: {}", e.getMessage(), e);
                } finally {
                    super.close(status, trailers);
                }
            }
        };
        
        // 建立包裝的 ServerCall.Listener 來追蹤請求
        ServerCall.Listener<ReqT> originalListener = next.startCall(wrappedCall, headers);
        
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(originalListener) {
            @Override
            public void onMessage(ReqT message) {
                try {
                    // 嘗試估算請求大小
                    if (message != null) {
                        requestSize.addAndGet(estimateMessageSize(message));
                    }
                } catch (Exception e) {
                    logger.debug("Could not estimate request message size: {}", e.getMessage());
                }
                super.onMessage(message);
            }
            
            @Override
            public void onCancel() {
                try {
                    // 記錄取消的呼叫
                    LocalDateTime callEndTime = LocalDateTime.now();
                    long executionTimeMs = java.time.Duration.between(callStartTime, callEndTime).toMillis();
                    
                    grpcCallLogService.logCallWithDetailsAsync(
                        clientIp,
                        targetLocation,
                        fullMethodName,
                        executionTimeMs,
                        "CANCELLED",
                        callStartTime,
                        callEndTime,
                        callType,
                        requestSize.get(),
                        responseSize.get(),
                        "Call was cancelled",
                        null,
                        null
                    );
                } catch (Exception e) {
                    logger.error("Error logging cancelled gRPC call: {}", e.getMessage(), e);
                } finally {
                    super.onCancel();
                }
            }
        };
    }
    
    /**
     * 提取客戶端 IP 地址
     */
    private String extractClientIp(ServerCall<?, ?> call) {
        try {
            Object remoteAddr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
            if (remoteAddr instanceof InetSocketAddress) {
                InetSocketAddress inetAddr = (InetSocketAddress) remoteAddr;
                return inetAddr.getAddress().getHostAddress();
            }
            return remoteAddr != null ? remoteAddr.toString() : "unknown";
        } catch (Exception e) {
            logger.debug("Could not extract client IP: {}", e.getMessage());
            return "unknown";
        }
    }
    
    /**
     * 提取目標位置資訊
     */
    private String extractTargetLocation(ServerCall<?, ?> call) {
        try {
            String authority = call.getAuthority();
            if (authority != null && !authority.isEmpty()) {
                return authority;
            }
            
            // 嘗試從其他屬性獲取目標資訊
            Object localAddr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_LOCAL_ADDR);
            if (localAddr instanceof InetSocketAddress) {
                InetSocketAddress inetAddr = (InetSocketAddress) localAddr;
                return inetAddr.getHostName() + ":" + inetAddr.getPort();
            }
            
            return "unknown";
        } catch (Exception e) {
            logger.debug("Could not extract target location: {}", e.getMessage());
            return "unknown";
        }
    }
    
    /**
     * 獲取呼叫類型
     */
    private String getCallType(MethodDescriptor<?, ?> method) {
        MethodDescriptor.MethodType type = method.getType();
        switch (type) {
            case UNARY:
                return "UNARY";
            case CLIENT_STREAMING:
                return "CLIENT_STREAMING";
            case SERVER_STREAMING:
                return "SERVER_STREAMING";
            case BIDI_STREAMING:
                return "BIDI_STREAMING";
            default:
                return "UNKNOWN";
        }
    }
    
    /**
     * 估算訊息大小
     */
    private long estimateMessageSize(Object message) {
        if (message == null) {
            return 0;
        }
        
        try {
            // 如果是 InputStream，嘗試獲取可用位元組數
            if (message instanceof java.io.InputStream) {
                java.io.InputStream inputStream = (java.io.InputStream) message;
                return inputStream.available();
            }
            
            // 如果是 ByteString，獲取大小
            if (message instanceof com.google.protobuf.ByteString) {
                com.google.protobuf.ByteString byteString = (com.google.protobuf.ByteString) message;
                return byteString.size();
            }
            
            // 如果是 protobuf 訊息，獲取序列化大小
            if (message instanceof com.google.protobuf.Message) {
                com.google.protobuf.Message protoMessage = (com.google.protobuf.Message) message;
                return protoMessage.getSerializedSize();
            }
            
            // 對於其他類型，使用字符串長度作為估算
            return message.toString().length();
            
        } catch (Exception e) {
            logger.debug("Could not estimate message size: {}", e.getMessage());
            return 0;
        }
    }
}