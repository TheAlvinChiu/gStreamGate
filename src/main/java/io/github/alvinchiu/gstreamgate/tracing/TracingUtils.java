package io.github.alvinchiu.gstreamgate.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Utility class for creating and managing custom spans
 * Provides convenience methods for tracing business logic
 */
@Component
public class TracingUtils {
    private static final Logger logger = LoggerFactory.getLogger(TracingUtils.class);
    
    private final Tracer tracer;
    private final boolean tracingEnabled;

    public TracingUtils(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("gstream-gate-business", "1.0.0");
        this.tracingEnabled = openTelemetry != OpenTelemetry.noop();
    }

    /**
     * Create a new span for connection pool operations
     */
    public Span createConnectionPoolSpan(String operation, String targetKey) {
        if (!tracingEnabled) {
            return Span.getInvalid();
        }

        return tracer.spanBuilder("connection_pool." + operation)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("operation", operation)
                .setAttribute("target.key", targetKey)
                .setAttribute("component", "connection_pool")
                .startSpan();
    }

    /**
     * Create a new span for circuit breaker operations
     */
    public Span createCircuitBreakerSpan(String operation, String targetKey, String state) {
        if (!tracingEnabled) {
            return Span.getInvalid();
        }

        return tracer.spanBuilder("circuit_breaker." + operation)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("operation", operation)
                .setAttribute("target.key", targetKey)
                .setAttribute("circuit_breaker.state", state)
                .setAttribute("component", "circuit_breaker")
                .startSpan();
    }

    /**
     * Create a new span for authentication operations
     */
    public Span createAuthSpan(String operation, String username) {
        if (!tracingEnabled) {
            return Span.getInvalid();
        }

        return tracer.spanBuilder("auth." + operation)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("operation", operation)
                .setAttribute("user.name", username)
                .setAttribute("component", "authentication")
                .startSpan();
    }

    /**
     * Create a new span for proxy configuration operations
     */
    public Span createProxyConfigSpan(String operation, String proxyKey) {
        if (!tracingEnabled) {
            return Span.getInvalid();
        }

        return tracer.spanBuilder("proxy_config." + operation)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("operation", operation)
                .setAttribute("proxy.key", proxyKey)
                .setAttribute("component", "proxy_configuration")
                .startSpan();
    }

    /**
     * Create a new span for database operations
     */
    public Span createDatabaseSpan(String operation, String table) {
        if (!tracingEnabled) {
            return Span.getInvalid();
        }

        return tracer.spanBuilder("db." + operation)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("db.operation", operation)
                .setAttribute("db.name", "gstreamgate")
                .setAttribute("db.sql.table", table)
                .setAttribute("component", "database")
                .startSpan();
    }

    /**
     * Execute a function within a span context
     */
    public <T> T executeWithSpan(Span span, String operation, SpanFunction<T> function) {
        if (!tracingEnabled || span == Span.getInvalid()) {
            try {
                return function.apply();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        try (Scope scope = span.makeCurrent()) {
            T result = function.apply();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.setAttribute("error.message", e.getMessage());
            span.setAttribute("error.type", e.getClass().getSimpleName());
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }

    /**
     * Execute a runnable within a span context
     */
    public void executeWithSpan(Span span, String operation, SpanRunnable runnable) {
        if (!tracingEnabled || span == Span.getInvalid()) {
            try {
                runnable.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return;
        }

        try (Scope scope = span.makeCurrent()) {
            runnable.run();
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.setAttribute("error.message", e.getMessage());
            span.setAttribute("error.type", e.getClass().getSimpleName());
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }

    /**
     * Add custom attributes to the current span
     */
    public void addSpanAttributes(String key, String value) {
        if (!tracingEnabled) {
            return;
        }

        Span currentSpan = Span.current();
        if (currentSpan != Span.getInvalid()) {
            currentSpan.setAttribute(key, value);
        }
    }

    /**
     * Add custom attributes to the current span
     */
    public void addSpanAttributes(String key, long value) {
        if (!tracingEnabled) {
            return;
        }

        Span currentSpan = Span.current();
        if (currentSpan != Span.getInvalid()) {
            currentSpan.setAttribute(key, value);
        }
    }

    /**
     * Add custom attributes to the current span
     */
    public void addSpanAttributes(String key, boolean value) {
        if (!tracingEnabled) {
            return;
        }

        Span currentSpan = Span.current();
        if (currentSpan != Span.getInvalid()) {
            currentSpan.setAttribute(key, value);
        }
    }

    /**
     * Add error information to the current span
     */
    public void addSpanError(Throwable throwable) {
        if (!tracingEnabled) {
            return;
        }

        Span currentSpan = Span.current();
        if (currentSpan != Span.getInvalid()) {
            currentSpan.setStatus(StatusCode.ERROR, throwable.getMessage());
            currentSpan.setAttribute("error.message", throwable.getMessage());
            currentSpan.setAttribute("error.type", throwable.getClass().getSimpleName());
            
            if (throwable.getCause() != null) {
                currentSpan.setAttribute("error.cause", throwable.getCause().getMessage());
            }
        }
    }

    /**
     * Get the current trace ID for logging correlation
     */
    public String getCurrentTraceId() {
        if (!tracingEnabled) {
            return "no-trace";
        }

        Span currentSpan = Span.current();
        if (currentSpan != Span.getInvalid()) {
            return currentSpan.getSpanContext().getTraceId();
        }
        return "no-trace";
    }

    /**
     * Get the current span ID for logging correlation
     */
    public String getCurrentSpanId() {
        if (!tracingEnabled) {
            return "no-span";
        }

        Span currentSpan = Span.current();
        if (currentSpan != Span.getInvalid()) {
            return currentSpan.getSpanContext().getSpanId();
        }
        return "no-span";
    }

    /**
     * Check if tracing is enabled
     */
    public boolean isTracingEnabled() {
        return tracingEnabled;
    }

    /**
     * Functional interface for span functions
     */
    @FunctionalInterface
    public interface SpanFunction<T> {
        T apply() throws Exception;
    }

    /**
     * Functional interface for span runnables
     */
    @FunctionalInterface
    public interface SpanRunnable {
        void run() throws Exception;
    }
}