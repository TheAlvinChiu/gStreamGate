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
 * gRPC Proxy 的 Metrics 收集系統 - 完全修復所有 API 問題
 * 收集性能指標、錯誤率、連接狀態等關鍵監控數據
 */
@Component
public class ProxyMetrics {
    private static final Logger logger = LoggerFactory.getLogger(ProxyMetrics.class);

    private final MeterRegistry meterRegistry;

    // 核心指標
    private final Timer requestTimer;
    private final Counter successCounter;
    private final Counter errorCounter;
    private final Counter connectionCounter;

    // 詳細統計
    private final Map<String, Timer> methodTimers = new ConcurrentHashMap<>();
    private final Map<String, Counter> methodCounters = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> activeConnectionsByTarget = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastRequestTimestamp = new ConcurrentHashMap<>();

    // 內部統計
    private final AtomicLong totalActiveConnections = new AtomicLong(0);
    private final AtomicLong totalRequestsProcessed = new AtomicLong(0);
    private final AtomicLong totalBytesTransferred = new AtomicLong(0);

    @Autowired
    public ProxyMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 初始化核心指標 - 修復 API 調用
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

        // 注册 Gauge - 使用 MeterRegistry 直接注册
        registerGauges();

        logger.info("ProxyMetrics initialized with {} registry", meterRegistry.getClass().getSimpleName());
    }

    /**
     * 注册 Gauge 指標 - 使用 MeterRegistry 直接注册
     */
    private void registerGauges() {
        // 活躍連接數 Gauge
        meterRegistry.gauge("grpc.proxy.connections.active",
                Tags.of("type", "active"),
                totalActiveConnections,
                AtomicLong::get);

        // 內存使用 Gauge
        meterRegistry.gauge("grpc.proxy.memory.usage",
                Tags.of("type", "heap"),
                this,
                ProxyMetrics::getMemoryUsage);

        // 總請求數 Gauge
        meterRegistry.gauge("grpc.proxy.requests.total",
                Tags.of("type", "processed"),
                totalRequestsProcessed,
                AtomicLong::get);

        // 總傳輸字節數 Gauge
        meterRegistry.gauge("grpc.proxy.bytes.transferred.total",
                Tags.of("type", "total"),
                totalBytesTransferred,
                AtomicLong::get);
    }

    /**
     * 記錄請求處理時間和結果
     */
    public void recordRequest(String method, String target, Duration duration, boolean success, long bytesTransferred) {
        // 記錄總體指標
        requestTimer.record(duration);
        totalRequestsProcessed.incrementAndGet();
        totalBytesTransferred.addAndGet(bytesTransferred);

        // 修復 Counter.increment() API 調用
        if (success) {
            // 創建帶標籤的 Counter 並增加
            Counter.builder("grpc.proxy.requests.success.tagged")
                    .description("Successful gRPC proxy requests with tags")
                    .tags("method", method, "target", target)
                    .register(meterRegistry)
                    .increment();
            // 同時增加總的成功計數器
            successCounter.increment();
        } else {
            // 創建帶標籤的 Counter 並增加
            Counter.builder("grpc.proxy.requests.error.tagged")
                    .description("Failed gRPC proxy requests with tags")
                    .tags("method", method, "target", target)
                    .register(meterRegistry)
                    .increment();
            // 同時增加總的錯誤計數器
            errorCounter.increment();
        }

        // 記錄按方法分類的指標
        recordMethodMetrics(method, target, duration, success);

        // 更新最後請求時間
        lastRequestTimestamp.put(target, new AtomicLong(System.currentTimeMillis()));

        logger.debug("Recorded request: method={}, target={}, duration={}ms, success={}, bytes={}",
                method, target, duration.toMillis(), success, bytesTransferred);
    }

    /**
     * 記錄按方法分類的指標
     */
    private void recordMethodMetrics(String method, String target, Duration duration, boolean success) {
        String methodKey = method + ":" + target;

        // 方法級別的計時器
        Timer methodTimer = methodTimers.computeIfAbsent(methodKey, key ->
                Timer.builder("grpc.proxy.method.duration")
                        .description("Method-specific request processing time")
                        .tags("method", method, "target", target)
                        .register(meterRegistry)
        );
        methodTimer.record(duration);

        // 方法級別的計數器
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
     * 記錄連接事件
     */
    public void recordConnection(String target, boolean connected) {
        // 修復 Counter.increment() 調用
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

        // 更新目標特定的 Gauge
        meterRegistry.gauge("grpc.proxy.connections.by.target",
                Tags.of("target", target),
                targetConnections,
                LongAdder::longValue);
    }

    /**
     * 記錄錯誤事件
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
     * 記錄流量統計
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
     * 記錄響應時間百分位數
     */
    public void recordResponseTimePercentiles(String target, Duration p50, Duration p95, Duration p99) {
        // 使用 MeterRegistry.gauge() 直接注册
        meterRegistry.gauge("grpc.proxy.response.time.p50",
                Tags.of("target", target), p50.toMillis());
        meterRegistry.gauge("grpc.proxy.response.time.p95",
                Tags.of("target", target), p95.toMillis());
        meterRegistry.gauge("grpc.proxy.response.time.p99",
                Tags.of("target", target), p99.toMillis());
    }

    /**
     * 創建自定義計時器
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * 停止計時器並記錄
     */
    public void stopTimer(Timer.Sample sample, String name, String... tagKeyValues) {
        Timer timer = Timer.builder(name)
                .tags(tagKeyValues)
                .register(meterRegistry);
        sample.stop(timer);
    }

    /**
     * 增加計數器
     */
    public void incrementCounter(String name, String... tagKeyValues) {
        Counter.builder(name)
                .tags(tagKeyValues)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 增加計數器指定數量
     */
    public void incrementCounter(String name, double amount, String... tagKeyValues) {
        Counter.builder(name)
                .tags(tagKeyValues)
                .register(meterRegistry)
                .increment(amount);
    }

    /**
     * 記錄 Gauge 值
     */
    public void recordGauge(String name, double value, String... tagKeyValues) {
        meterRegistry.gauge(name, Tags.of(tagKeyValues), value);
    }

    /**
     * 獲取總活躍連接數
     */
    public long getTotalActiveConnections() {
        return totalActiveConnections.get();
    }

    /**
     * 獲取指定目標的活躍連接數
     */
    public long getActiveConnectionsForTarget(String target) {
        LongAdder adder = activeConnectionsByTarget.get(target);
        return adder != null ? adder.longValue() : 0;
    }

    /**
     * 獲取內存使用量
     */
    public double getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * 獲取總處理請求數
     */
    public long getTotalRequestsProcessed() {
        return totalRequestsProcessed.get();
    }

    /**
     * 獲取總傳輸字節數
     */
    public long getTotalBytesTransferred() {
        return totalBytesTransferred.get();
    }

    /**
     * 獲取目標服務的最後請求時間
     */
    public long getLastRequestTimestamp(String target) {
        AtomicLong timestamp = lastRequestTimestamp.get(target);
        return timestamp != null ? timestamp.get() : 0;
    }

    /**
     * 獲取錯誤率
     */
    public double getErrorRate() {
        double totalSuccess = successCounter.count();
        double totalError = errorCounter.count();
        double total = totalSuccess + totalError;

        return total > 0 ? totalError / total : 0.0;
    }

    /**
     * 獲取平均響應時間
     */
    public double getAverageResponseTime() {
        return requestTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 獲取 MeterRegistry（用於高級用法）
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    /**
     * 獲取綜合統計信息
     */
    public MetricsSummary getSummary() {
        return new MetricsSummary(
                getTotalRequestsProcessed(),
                getTotalActiveConnections(),
                getErrorRate(),
                getAverageResponseTime(),
                getTotalBytesTransferred(),
                (long) getMemoryUsage(),
                activeConnectionsByTarget.size() // 目標數量
        );
    }

    /**
     * 重置統計信息（謹慎使用）
     */
    public void reset() {
        logger.warn("Resetting all metrics - this should only be done for testing!");

        totalActiveConnections.set(0);
        totalRequestsProcessed.set(0);
        totalBytesTransferred.set(0);

        activeConnectionsByTarget.clear();
        lastRequestTimestamp.clear();

        // 清理緩存的計時器和計數器
        methodTimers.clear();
        methodCounters.clear();
    }

    /**
     * Metrics 綜合摘要
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