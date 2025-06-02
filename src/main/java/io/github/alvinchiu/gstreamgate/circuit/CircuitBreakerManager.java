package io.github.alvinchiu.gstreamgate.circuit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 熔斷器管理系統
 * 為每個上游服務提供熔斷保護，防止級聯故障
 */
@Component
public class CircuitBreakerManager {
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerManager.class);

    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    /**
     * 獲取指定服務的熔斷器
     */
    public CircuitBreaker getCircuitBreaker(String serviceName) {
        return circuitBreakers.computeIfAbsent(serviceName, this::createCircuitBreaker);
    }

    /**
     * 創建新的熔斷器
     */
    private CircuitBreaker createCircuitBreaker(String serviceName) {
        CircuitBreaker breaker = new CircuitBreaker(serviceName);
        logger.info("Created circuit breaker for service: {}", serviceName);
        return breaker;
    }

    /**
     * 執行受熔斷器保護的操作
     */
    public <T> T execute(String serviceName, Supplier<T> operation) throws CircuitBreakerOpenException {
        CircuitBreaker breaker = getCircuitBreaker(serviceName);
        return breaker.execute(operation);
    }

    /**
     * 獲取所有熔斷器的狀態
     */
    public Map<String, CircuitBreakerStatus> getAllCircuitBreakerStatus() {
        Map<String, CircuitBreakerStatus> statusMap = new ConcurrentHashMap<>();
        circuitBreakers.forEach((name, breaker) -> statusMap.put(name, breaker.getStatus()));
        return statusMap;
    }

    /**
     * 手動重置熔斷器
     */
    public void resetCircuitBreaker(String serviceName) {
        CircuitBreaker breaker = circuitBreakers.get(serviceName);
        if (breaker != null) {
            breaker.reset();
            logger.info("Circuit breaker reset for service: {}", serviceName);
        }
    }

    /**
     * 熔斷器實現
     */
    public static class CircuitBreaker {
        private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

        // 配置參數
        private static final int DEFAULT_FAILURE_THRESHOLD = 5;           // 失敗閾值
        private static final double DEFAULT_FAILURE_RATE_THRESHOLD = 0.5; // 失敗率閾值 (50%)
        private static final int DEFAULT_MINIMUM_REQUESTS = 10;           // 最小請求數
        private static final Duration DEFAULT_WAIT_DURATION = Duration.ofSeconds(60); // 等待時間
        private static final int DEFAULT_SLIDING_WINDOW_SIZE = 100;       // 滑動窗口大小

        private final String serviceName;
        private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicLong lastFailureTime = new AtomicLong(0);
        private final AtomicLong stateTransitionTime = new AtomicLong(System.currentTimeMillis());

        // 滑動窗口統計
        private final SlidingWindow slidingWindow = new SlidingWindow(DEFAULT_SLIDING_WINDOW_SIZE);

        public CircuitBreaker(String serviceName) {
            this.serviceName = serviceName;
        }

        /**
         * 執行受保護的操作
         */
        public <T> T execute(Supplier<T> operation) throws CircuitBreakerOpenException {
            State currentState = state.get();

            switch (currentState) {
                case OPEN:
                    if (shouldAttemptReset()) {
                        state.compareAndSet(State.OPEN, State.HALF_OPEN);
                        logger.debug("Circuit breaker for {} transitioned from OPEN to HALF_OPEN", serviceName);
                        stateTransitionTime.set(System.currentTimeMillis());
                    } else {
                        throw new CircuitBreakerOpenException("Circuit breaker is OPEN for service: " + serviceName);
                    }
                    break;
                case HALF_OPEN:
                    // 在半開狀態下允許少量請求通過
                    break;
                case CLOSED:
                    // 正常狀態，允許所有請求
                    break;
            }

            try {
                T result = operation.get();
                onSuccess();
                return result;
            } catch (Exception e) {
                onFailure();
                throw e;
            }
        }

        /**
         * 處理成功情況
         */
        private void onSuccess() {
            slidingWindow.recordSuccess();
            successCount.incrementAndGet();

            State currentState = state.get();

            if (currentState == State.HALF_OPEN) {
                // 半開狀態下成功，嘗試關閉熔斷器
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    reset();
                    logger.info("Circuit breaker for {} transitioned from HALF_OPEN to CLOSED", serviceName);
                    stateTransitionTime.set(System.currentTimeMillis());
                }
            }
        }

        /**
         * 處理失敗情況
         */
        private void onFailure() {
            slidingWindow.recordFailure();
            failureCount.incrementAndGet();
            lastFailureTime.set(System.currentTimeMillis());

            State currentState = state.get();

            if (currentState == State.HALF_OPEN) {
                // 半開狀態下失敗，立即打開熔斷器
                if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                    logger.warn("Circuit breaker for {} transitioned from HALF_OPEN to OPEN due to failure", serviceName);
                    stateTransitionTime.set(System.currentTimeMillis());
                }
            } else if (currentState == State.CLOSED) {
                // 檢查是否需要打開熔斷器
                if (shouldOpenCircuit()) {
                    if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                        logger.warn("Circuit breaker for {} transitioned from CLOSED to OPEN. " +
                                        "Failure count: {}, Failure rate: {:.2f}%",
                                serviceName, failureCount.get(), slidingWindow.getFailureRate() * 100);
                        stateTransitionTime.set(System.currentTimeMillis());
                    }
                }
            }
        }

        /**
         * 判斷是否應該打開熔斷器
         */
        private boolean shouldOpenCircuit() {
            int totalRequests = slidingWindow.getTotalRequests();

            // 請求數量不足，不打開熔斷器
            if (totalRequests < DEFAULT_MINIMUM_REQUESTS) {
                return false;
            }

            // 檢查失敗率
            double failureRate = slidingWindow.getFailureRate();
            boolean shouldOpen = failureRate >= DEFAULT_FAILURE_RATE_THRESHOLD;

            if (shouldOpen) {
                logger.debug("Circuit breaker conditions met for {}: totalRequests={}, failureRate={:.2f}%, threshold={:.2f}%",
                        serviceName, totalRequests, failureRate * 100, DEFAULT_FAILURE_RATE_THRESHOLD * 100);
            }

            return shouldOpen;
        }

        /**
         * 判斷是否應該嘗試重置
         */
        private boolean shouldAttemptReset() {
            long currentTime = System.currentTimeMillis();
            long timeSinceStateTransition = currentTime - stateTransitionTime.get();
            return timeSinceStateTransition >= DEFAULT_WAIT_DURATION.toMillis();
        }

        /**
         * 重置熔斷器
         */
        public void reset() {
            failureCount.set(0);
            successCount.set(0);
            lastFailureTime.set(0);
            slidingWindow.reset();
            state.set(State.CLOSED);
            stateTransitionTime.set(System.currentTimeMillis());
            logger.info("Circuit breaker reset for service: {}", serviceName);
        }

        /**
         * 獲取熔斷器狀態
         */
        public CircuitBreakerStatus getStatus() {
            return new CircuitBreakerStatus(
                    serviceName,
                    state.get(),
                    failureCount.get(),
                    successCount.get(),
                    slidingWindow.getFailureRate(),
                    slidingWindow.getTotalRequests(),
                    Instant.ofEpochMilli(lastFailureTime.get()),
                    Instant.ofEpochMilli(stateTransitionTime.get())
            );
        }
    }

    /**
     * 滑動窗口統計實現
     */
    private static class SlidingWindow {
        private final int windowSize;
        private final AtomicInteger totalRequests = new AtomicInteger(0);
        private final AtomicInteger failureRequests = new AtomicInteger(0);
        private final AtomicInteger currentIndex = new AtomicInteger(0);
        private final boolean[] window;
        private final Object lock = new Object();

        public SlidingWindow(int windowSize) {
            this.windowSize = windowSize;
            this.window = new boolean[windowSize];
        }

        /**
         * 記錄成功
         */
        public void recordSuccess() {
            record(true);
        }

        /**
         * 記錄失敗
         */
        public void recordFailure() {
            record(false);
        }

        /**
         * 記錄請求結果
         */
        private void record(boolean success) {
            synchronized (lock) {
                int index = currentIndex.getAndIncrement() % windowSize;

                // 如果窗口已滿，需要移除舊的記錄
                if (totalRequests.get() >= windowSize) {
                    boolean oldValue = window[index];
                    if (!oldValue) { // 舊記錄是失敗
                        failureRequests.decrementAndGet();
                    }
                } else {
                    totalRequests.incrementAndGet();
                }

                // 添加新記錄
                window[index] = success;
                if (!success) {
                    failureRequests.incrementAndGet();
                }
            }
        }

        /**
         * 獲取失敗率
         */
        public double getFailureRate() {
            int total = totalRequests.get();
            if (total == 0) {
                return 0.0;
            }
            return (double) failureRequests.get() / total;
        }

        /**
         * 獲取總請求數
         */
        public int getTotalRequests() {
            return totalRequests.get();
        }

        /**
         * 獲取失敗請求數
         */
        public int getFailureRequests() {
            return failureRequests.get();
        }

        /**
         * 重置窗口
         */
        public void reset() {
            synchronized (lock) {
                totalRequests.set(0);
                failureRequests.set(0);
                currentIndex.set(0);
                for (int i = 0; i < window.length; i++) {
                    window[i] = false;
                }
            }
        }
    }

    /**
     * 熔斷器狀態枚舉
     */
    public enum State {
        CLOSED,    // 關閉狀態，正常處理請求
        OPEN,      // 打開狀態，拒絕所有請求
        HALF_OPEN  // 半開狀態，允許少量請求通過測試
    }

    /**
     * 熔斷器狀態信息
     */
    public static class CircuitBreakerStatus {
        private final String serviceName;
        private final State state;
        private final int failureCount;
        private final int successCount;
        private final double failureRate;
        private final int totalRequests;
        private final Instant lastFailureTime;
        private final Instant stateTransitionTime;

        public CircuitBreakerStatus(String serviceName, State state, int failureCount, int successCount,
                                    double failureRate, int totalRequests, Instant lastFailureTime,
                                    Instant stateTransitionTime) {
            this.serviceName = serviceName;
            this.state = state;
            this.failureCount = failureCount;
            this.successCount = successCount;
            this.failureRate = failureRate;
            this.totalRequests = totalRequests;
            this.lastFailureTime = lastFailureTime;
            this.stateTransitionTime = stateTransitionTime;
        }

        // Getters
        public String getServiceName() { return serviceName; }
        public State getState() { return state; }
        public int getFailureCount() { return failureCount; }
        public int getSuccessCount() { return successCount; }
        public double getFailureRate() { return failureRate; }
        public int getTotalRequests() { return totalRequests; }
        public Instant getLastFailureTime() { return lastFailureTime; }
        public Instant getStateTransitionTime() { return stateTransitionTime; }

        @Override
        public String toString() {
            return String.format("CircuitBreakerStatus{service='%s', state=%s, failures=%d, successes=%d, " +
                            "failureRate=%.2f%%, totalRequests=%d}",
                    serviceName, state, failureCount, successCount, failureRate * 100, totalRequests);
        }
    }

    /**
     * 熔斷器打開異常
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}