package io.github.alvinchiu.gstreamgate.metrics;

import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics collection system for the gRPC proxy with all API issues fixed.
 * Collects performance metrics, error rates, connection states and other
 * critical monitoring data.
 */
@Component
public class ProxyMetrics {
    private static final Logger logger = LoggerFactory.getLogger(ProxyMetrics.class);

    private final MeterRegistry meterRegistry;

    // Core metrics
    private final Timer requestTimer;
    private final Counter successCounter;
    private final Counter errorCounter;
    private final Counter connectionCounter;

    // Detailed statistics
    private final Map<String, Timer> methodTimers = new ConcurrentHashMap<>();
    private final Map<String, Counter> methodCounters = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> activeConnectionsByTarget = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastRequestTimestamp = new ConcurrentHashMap<>();

    // Internal statistics
    private final AtomicLong totalActiveConnections = new AtomicLong(0);
    private final AtomicLong totalRequestsProcessed = new AtomicLong(0);
    private final AtomicLong totalBytesTransferred = new AtomicLong(0);

    @Autowired
    public ProxyMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize core metrics - fixed API calls
        this.requestTimer = Timer.builder("grpc.proxy.request.duration")
                .description("gRPC proxy request processing time")
                .register(meterRegistry);

        this.successCounter = Counter.builder("grpc.proxy.requests.success")
                .description("Successful gRPC proxy requests")
                .register(meterRegistry);

        this.errorCounter = Counter.builder("grpc.proxy.requests.error")
                .description("Failed gRPC proxy requests")
                .register(meterRegistry);

        this.connectionCounter = Counter.builder("grpc.proxy.connections.total")
                .description("Total gRPC proxy connections created")
                .register(meterRegistry);

        // Register gauges directly with the MeterRegistry
        registerGauges();

        logger.info("ProxyMetrics initialized with {} registry", meterRegistry.getClass().getSimpleName());
    }

    /**
     * Register Gauge metrics directly with the MeterRegistry
     */
    private void registerGauges() {
        // Active connection gauge
        meterRegistry.gauge("grpc.proxy.connections.active",
                Tags.of("type", "active"),
                totalActiveConnections,
                AtomicLong::get);

        // Memory usage gauge
        meterRegistry.gauge("grpc.proxy.memory.usage",
                Tags.of("type", "heap"),
                this,
                ProxyMetrics::getMemoryUsage);

        // Total request gauge
        meterRegistry.gauge("grpc.proxy.requests.total",
                Tags.of("type", "processed"),
                totalRequestsProcessed,
                AtomicLong::get);

        // Total bytes transferred gauge
        meterRegistry.gauge("grpc.proxy.bytes.transferred.total",
                Tags.of("type", "total"),
                totalBytesTransferred,
                AtomicLong::get);
    }

    /**
     * Record the request processing time and result
     */
    public void recordRequest(String method, String target, Duration duration, boolean success, long bytesTransferred) {
        // Record overall metrics
        requestTimer.record(duration);
        totalRequestsProcessed.incrementAndGet();
        totalBytesTransferred.addAndGet(bytesTransferred);

        // Fixed Counter.increment() API usage
        if (success) {
            // Create a labeled Counter and increment
            Counter.builder("grpc.proxy.requests.success.tagged")
                    .description("Successful gRPC proxy requests with tags")
                    .tags("method", method, "target", target)
                    .register(meterRegistry)
                    .increment();
            // Also increment the global success counter
            successCounter.increment();
        } else {
            // Create a labeled Counter and increment
            Counter.builder("grpc.proxy.requests.error.tagged")
                    .description("Failed gRPC proxy requests with tags")
                    .tags("method", method, "target", target)
                    .register(meterRegistry)
                    .increment();
            // Also increment the global error counter
            errorCounter.increment();
        }

        // Record method-specific metrics
        recordMethodMetrics(method, target, duration, success);

        // Update the last request time
        lastRequestTimestamp.put(target, new AtomicLong(System.currentTimeMillis()));

        logger.debug("Recorded request: method={}, target={}, duration={}ms, success={}, bytes={}",
                method, target, duration.toMillis(), success, bytesTransferred);
    }

    /**
     * Record metrics categorized by method
     */
    private void recordMethodMetrics(String method, String target, Duration duration, boolean success) {
        String methodKey = method + ":" + target;

        // Method-level timer
        Timer methodTimer = methodTimers.computeIfAbsent(methodKey, key ->
                Timer.builder("grpc.proxy.method.duration")
                        .description("Method-specific request processing time")
                        .tags("method", method, "target", target)
                        .register(meterRegistry)
        );
        methodTimer.record(duration);

        // Method-level counter
        String counterKey = methodKey + ":" + (success ? "success" : "error");
        Counter methodCounter = methodCounters.computeIfAbsent(counterKey, key ->
                Counter.builder("grpc.proxy.method.requests")
                        .description("Method-specific request count")
                        .tags("method", method, "target", target, "status", success ? "success" : "error")
                        .register(meterRegistry)
        );
        methodCounter.increment();
    }

    /**
     * Record connection events
     */
    public void recordConnection(String target, boolean connected) {
        // Fixed Counter.increment() call
        Counter.builder("grpc.proxy.connections.events")
                .description("Connection events")
                .tags("target", target, "action", connected ? "connect" : "disconnect")
                .register(meterRegistry)
                .increment();

        connectionCounter.increment();

        LongAdder targetConnections = activeConnectionsByTarget.computeIfAbsent(target, k -> new LongAdder());

        if (connected) {
            targetConnections.increment();
            totalActiveConnections.incrementAndGet();
            logger.debug("Connection established to target: {}, total: {}", target, getTotalActiveConnections());
        } else {
            targetConnections.decrement();
            totalActiveConnections.decrementAndGet();
            logger.debug("Connection closed to target: {}, total: {}", target, getTotalActiveConnections());
        }

        // Update the target-specific gauge
        meterRegistry.gauge("grpc.proxy.connections.by.target",
                Tags.of("target", target),
                targetConnections,
                LongAdder::longValue);
    }

    /**
     * Record error events
     */
    public void recordError(String target, String errorType, String errorMessage) {
        Counter.builder("grpc.proxy.errors")
                .description("Specific error types in gRPC proxy")
                .tags("target", target, "error_type", errorType)
                .register(meterRegistry)
                .increment();

        logger.debug("Recorded error: target={}, type={}, message={}", target, errorType, errorMessage);
    }

    /**
     * Record traffic statistics
     */
    public void recordTraffic(String target, long inboundBytes, long outboundBytes) {
        if (inboundBytes > 0) {
            Counter.builder("grpc.proxy.traffic.inbound")
                    .description("Inbound traffic through gRPC proxy")
                    .baseUnit("bytes")
                    .tags("target", target)
                    .register(meterRegistry)
                    .increment(inboundBytes);
        }

        if (outboundBytes > 0) {
            Counter.builder("grpc.proxy.traffic.outbound")
                    .description("Outbound traffic through gRPC proxy")
                    .baseUnit("bytes")
                    .tags("target", target)
                    .register(meterRegistry)
                    .increment(outboundBytes);
        }

        totalBytesTransferred.addAndGet(inboundBytes + outboundBytes);
    }

    /**
     * Record response time percentiles
     */
    public void recordResponseTimePercentiles(String target, Duration p50, Duration p95, Duration p99) {
        // Directly register using MeterRegistry.gauge()
        meterRegistry.gauge("grpc.proxy.response.time.p50",
                Tags.of("target", target), p50.toMillis());
        meterRegistry.gauge("grpc.proxy.response.time.p95",
                Tags.of("target", target), p95.toMillis());
        meterRegistry.gauge("grpc.proxy.response.time.p99",
                Tags.of("target", target), p99.toMillis());
    }

    /**
     * Create a custom timer
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Stop the timer and record
     */
    public void stopTimer(Timer.Sample sample, String name, String... tagKeyValues) {
        Timer timer = Timer.builder(name)
                .tags(tagKeyValues)
                .register(meterRegistry);
        sample.stop(timer);
    }

    /**
     * Increment a counter
     */
    public void incrementCounter(String name, String... tagKeyValues) {
        Counter.builder(name)
                .tags(tagKeyValues)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Increment a counter by a specific amount
     */
    public void incrementCounter(String name, double amount, String... tagKeyValues) {
        Counter.builder(name)
                .tags(tagKeyValues)
                .register(meterRegistry)
                .increment(amount);
    }

    /**
     * Record a gauge value
     */
    public void recordGauge(String name, double value, String... tagKeyValues) {
        meterRegistry.gauge(name, Tags.of(tagKeyValues), value);
    }

    /**
     * Get total active connections
     */
    public long getTotalActiveConnections() {
        return totalActiveConnections.get();
    }

    /**
     * Get active connections for the specified target
     */
    public long getActiveConnectionsForTarget(String target) {
        LongAdder adder = activeConnectionsByTarget.get(target);
        return adder != null ? adder.longValue() : 0;
    }

    /**
     * Get memory usage
     */
    public double getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * Get total processed requests
     */
    public long getTotalRequestsProcessed() {
        return totalRequestsProcessed.get();
    }

    /**
     * Get total transferred bytes
     */
    public long getTotalBytesTransferred() {
        return totalBytesTransferred.get();
    }

    /**
     * Get the last request time for a target
     */
    public long getLastRequestTimestamp(String target) {
        AtomicLong timestamp = lastRequestTimestamp.get(target);
        return timestamp != null ? timestamp.get() : 0;
    }

    /**
     * Get the error rate
     */
    public double getErrorRate() {
        double totalSuccess = successCounter.count();
        double totalError = errorCounter.count();
        double total = totalSuccess + totalError;

        return total > 0 ? totalError / total : 0.0;
    }

    /**
     * Get the average response time
     */
    public double getAverageResponseTime() {
        return requestTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Get the MeterRegistry (for advanced usage)
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    /**
     * Get aggregated statistics
     */
    public MetricsSummary getSummary() {
        return new MetricsSummary(
                getTotalRequestsProcessed(),
                getTotalActiveConnections(),
                getErrorRate(),
                getAverageResponseTime(),
                getTotalBytesTransferred(),
                (long) getMemoryUsage(),
                activeConnectionsByTarget.size() // number of targets
        );
    }

    /**
     * Reset metrics (use with caution)
     */
    public void reset() {
        logger.warn("Resetting all metrics - this should only be done for testing!");

        totalActiveConnections.set(0);
        totalRequestsProcessed.set(0);
        totalBytesTransferred.set(0);

        activeConnectionsByTarget.clear();
        lastRequestTimestamp.clear();

        // Clear cached timers and counters
        methodTimers.clear();
        methodCounters.clear();
    }

    /**
     * Metrics summary
     */
    public static class MetricsSummary {
        private final long totalRequests;
        private final long activeConnections;
        private final double errorRate;
        private final double averageResponseTime;
        private final long totalBytesTransferred;
        private final long memoryUsage;
        private final int targetCount;

        public MetricsSummary(long totalRequests, long activeConnections, double errorRate,
                              double averageResponseTime, long totalBytesTransferred,
                              long memoryUsage, int targetCount) {
            this.totalRequests = totalRequests;
            this.activeConnections = activeConnections;
            this.errorRate = errorRate;
            this.averageResponseTime = averageResponseTime;
            this.totalBytesTransferred = totalBytesTransferred;
            this.memoryUsage = memoryUsage;
            this.targetCount = targetCount;
        }

        // Getters
        public long getTotalRequests() { return totalRequests; }
        public long getActiveConnections() { return activeConnections; }
        public double getErrorRate() { return errorRate; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public long getTotalBytesTransferred() { return totalBytesTransferred; }
        public long getMemoryUsage() { return memoryUsage; }
        public int getTargetCount() { return targetCount; }

        @Override
        public String toString() {
            return String.format("MetricsSummary{requests=%d, connections=%d, errorRate=%.2f%%, " +
                            "avgResponseTime=%.2fms, bytesTransferred=%d, memoryUsage=%dMB, targets=%d}",
                    totalRequests, activeConnections, errorRate * 100, averageResponseTime,
                    totalBytesTransferred, memoryUsage / 1024 / 1024, targetCount);
        }
    }
}