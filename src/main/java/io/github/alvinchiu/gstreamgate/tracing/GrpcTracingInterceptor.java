package io.github.alvinchiu.gstreamgate.tracing;

import io.github.alvinchiu.gstreamgate.service.GrpcCallLogService;
import io.grpc.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * gRPC interceptor for OpenTelemetry tracing
 * Provides comprehensive request tracing for both client and server calls
 */
@Component
public class GrpcTracingInterceptor implements ServerInterceptor, ClientInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(GrpcTracingInterceptor.class);
    
    private final Tracer tracer;
    private final boolean tracingEnabled;
    private final GrpcCallLogService grpcCallLogService;

    @Autowired
    public GrpcTracingInterceptor(OpenTelemetry openTelemetry, GrpcCallLogService grpcCallLogService) {
        this.tracer = openTelemetry.getTracer("gstream-gate-grpc", "1.0.0");
        this.tracingEnabled = openTelemetry != OpenTelemetry.noop();
        this.grpcCallLogService = grpcCallLogService;
        
        if (tracingEnabled) {
            logger.info("gRPC tracing interceptor initialized with call logging");
        } else {
            logger.info("gRPC tracing interceptor initialized (tracing disabled) with call logging");
        }
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        
        if (!tracingEnabled) {
            return next.startCall(call, headers);
        }

        String fullMethodName = call.getMethodDescriptor().getFullMethodName();
        String serviceName = extractServiceName(fullMethodName);
        String methodName = extractMethodName(fullMethodName);

        // Start server span
        Span span = tracer.spanBuilder("grpc.server/" + methodName)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("rpc.system", "grpc")
                .setAttribute("rpc.service", serviceName)
                .setAttribute("rpc.method", methodName)
                .setAttribute("rpc.grpc.status_code", "0")
                .setAttribute("grpc.full_method_name", fullMethodName)
                .setAttribute("grpc.call_type", getCallType(call.getMethodDescriptor()))
                .startSpan();

        // Extract remote address if available
        String remoteAddress = extractRemoteAddress(call);
        if (remoteAddress != null) {
            span.setAttribute("client.address", remoteAddress);
        }

        Context context = Context.current().with(span);
        
        try (Scope scope = context.makeCurrent()) {
            return new TracingServerCallListener<>(
                    next.startCall(new TracingServerCall<>(call, span), headers),
                    span);
        }
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {
        
        if (!tracingEnabled) {
            return next.newCall(method, callOptions);
        }

        String fullMethodName = method.getFullMethodName();
        String serviceName = extractServiceName(fullMethodName);
        String methodName = extractMethodName(fullMethodName);

        // Start client span
        Span span = tracer.spanBuilder("grpc.client/" + methodName)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("rpc.system", "grpc")
                .setAttribute("rpc.service", serviceName)
                .setAttribute("rpc.method", methodName)
                .setAttribute("grpc.full_method_name", fullMethodName)
                .setAttribute("grpc.call_type", getCallType(method))
                .startSpan();

        Context context = Context.current().with(span);
        
        try (Scope scope = context.makeCurrent()) {
            return new TracingClientCall<>(
                    next.newCall(method, callOptions), 
                    span);
        }
    }

    /**
     * Extract service name from full method name
     */
    private String extractServiceName(String fullMethodName) {
        int lastSlash = fullMethodName.lastIndexOf('/');
        if (lastSlash == -1) {
            return "unknown";
        }
        return fullMethodName.substring(0, lastSlash);
    }

    /**
     * Extract method name from full method name
     */
    private String extractMethodName(String fullMethodName) {
        int lastSlash = fullMethodName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == fullMethodName.length() - 1) {
            return fullMethodName;
        }
        return fullMethodName.substring(lastSlash + 1);
    }

    /**
     * Get call type string
     */
    private String getCallType(MethodDescriptor<?, ?> method) {
        MethodDescriptor.MethodType type = method.getType();
        switch (type) {
            case UNARY:
                return "unary";
            case CLIENT_STREAMING:
                return "client_streaming";
            case SERVER_STREAMING:
                return "server_streaming";
            case BIDI_STREAMING:
                return "bidi_streaming";
            default:
                return "unknown";
        }
    }

    /**
     * Extract remote address from server call
     */
    private String extractRemoteAddress(ServerCall<?, ?> call) {
        try {
            return call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR).toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Traced server call wrapper
     */
    private static class TracingServerCall<ReqT, RespT> extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {
        private final Span span;

        TracingServerCall(ServerCall<ReqT, RespT> delegate, Span span) {
            super(delegate);
            this.span = span;
        }

        @Override
        public void close(Status status, Metadata trailers) {
            try {
                span.setAttribute("rpc.grpc.status_code", String.valueOf(status.getCode().value()));
                
                if (status.isOk()) {
                    span.setStatus(StatusCode.OK);
                } else {
                    span.setStatus(StatusCode.ERROR, status.getDescription());
                    span.setAttribute("grpc.error_message", status.getDescription());
                }
            } finally {
                span.end();
                super.close(status, trailers);
            }
        }
    }

    /**
     * Traced server call listener wrapper
     */
    private static class TracingServerCallListener<ReqT> extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
        private final Span span;

        TracingServerCallListener(ServerCall.Listener<ReqT> delegate, Span span) {
            super(delegate);
            this.span = span;
        }

        @Override
        public void onMessage(ReqT message) {
            try (Scope scope = span.makeCurrent()) {
                super.onMessage(message);
            }
        }

        @Override
        public void onHalfClose() {
            try (Scope scope = span.makeCurrent()) {
                super.onHalfClose();
            }
        }

        @Override
        public void onCancel() {
            try {
                span.setStatus(StatusCode.ERROR, "Cancelled");
                span.setAttribute("grpc.cancelled", "true");
            } finally {
                span.end();
                super.onCancel();
            }
        }
    }

    /**
     * Traced client call wrapper
     */
    private static class TracingClientCall<ReqT, RespT> extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {
        private final Span span;
        private final AtomicReference<Status> finalStatus = new AtomicReference<>();

        TracingClientCall(ClientCall<ReqT, RespT> delegate, Span span) {
            super(delegate);
            this.span = span;
        }

        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
            super.start(new TracingClientCallListener<>(responseListener, span, finalStatus), headers);
        }

        @Override
        public void cancel(String message, Throwable cause) {
            try {
                span.setStatus(StatusCode.ERROR, message);
                span.setAttribute("grpc.cancelled", "true");
                if (cause != null) {
                    span.setAttribute("grpc.error_message", cause.getMessage());
                }
            } finally {
                span.end();
                super.cancel(message, cause);
            }
        }
    }

    /**
     * Traced client call listener wrapper
     */
    private static class TracingClientCallListener<RespT> extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {
        private final Span span;
        private final AtomicReference<Status> finalStatus;

        TracingClientCallListener(ClientCall.Listener<RespT> delegate, Span span, AtomicReference<Status> finalStatus) {
            super(delegate);
            this.span = span;
            this.finalStatus = finalStatus;
        }

        @Override
        public void onMessage(RespT message) {
            try (Scope scope = span.makeCurrent()) {
                super.onMessage(message);
            }
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            try {
                finalStatus.set(status);
                span.setAttribute("rpc.grpc.status_code", String.valueOf(status.getCode().value()));
                
                if (status.isOk()) {
                    span.setStatus(StatusCode.OK);
                } else {
                    span.setStatus(StatusCode.ERROR, status.getDescription());
                    span.setAttribute("grpc.error_message", status.getDescription());
                }
            } finally {
                span.end();
                super.onClose(status, trailers);
            }
        }
    }
}