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
 * Circuit breaker management system.
 * Provides protection for each upstream service to avoid cascading failures.
 */
@Component
public class CircuitBreakerManager {
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerManager.class);

    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    /**
     * Retrieve the circuit breaker for the specified service.
     */
    public CircuitBreaker getCircuitBreaker(String serviceName) {
        return circuitBreakers.computeIfAbsent(serviceName, this::createCircuitBreaker);
    }

    /**
     * Create a new circuit breaker.
     */
    private CircuitBreaker createCircuitBreaker(String serviceName) {
        CircuitBreaker breaker = new CircuitBreaker(serviceName);
        logger.info("Created circuit breaker for service: {}", serviceName);
        return breaker;
    }

    /**
     * Execute an operation protected by a circuit breaker.
     */
    public <T> T execute(String serviceName, Supplier<T> operation) throws CircuitBreakerOpenException {
        CircuitBreaker breaker = getCircuitBreaker(serviceName);
        return breaker.execute(operation);
    }

    /**
     * Get the status of all circuit breakers.
     */
    public Map<String, CircuitBreakerStatus> getAllCircuitBreakerStatus() {
        Map<String, CircuitBreakerStatus> statusMap = new ConcurrentHashMap<>();
        circuitBreakers.forEach((name, breaker) -> statusMap.put(name, breaker.getStatus()));
        return statusMap;
    }

    /**
     * Manually reset a circuit breaker.
     */
    public void resetCircuitBreaker(String serviceName) {
        CircuitBreaker breaker = circuitBreakers.get(serviceName);
        if (breaker != null) {
            breaker.reset();
            logger.info("Circuit breaker reset for service: {}", serviceName);
        }
    }

    /**
     * Circuit breaker implementation.
     */
    public static class CircuitBreaker {
        private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

        // Configuration parameters
        private static final int DEFAULT_FAILURE_THRESHOLD = 5;           // Failure threshold
        private static final double DEFAULT_FAILURE_RATE_THRESHOLD = 0.5; // Failure rate threshold (50%)
        private static final int DEFAULT_MINIMUM_REQUESTS = 10;           // Minimum request count
        private static final Duration DEFAULT_WAIT_DURATION = Duration.ofSeconds(60); // Wait duration
        private static final int DEFAULT_SLIDING_WINDOW_SIZE = 100;       // Sliding window size

        private final String serviceName;
        private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicLong lastFailureTime = new AtomicLong(0);
        private final AtomicLong stateTransitionTime = new AtomicLong(System.currentTimeMillis());

        // Sliding window statistics
        private final SlidingWindow slidingWindow = new SlidingWindow(DEFAULT_SLIDING_WINDOW_SIZE);

        public CircuitBreaker(String serviceName) {
            this.serviceName = serviceName;
        }

        /**
         * Execute the protected operation.
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
                    // Allow a small number of requests in HALF_OPEN state
                    break;
                case CLOSED:
                    // Normal state, all requests are allowed
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
         * Handle a successful execution.
         */
        private void onSuccess() {
            slidingWindow.recordSuccess();
            successCount.incrementAndGet();

            State currentState = state.get();

            if (currentState == State.HALF_OPEN) {
                // Success in HALF_OPEN state, attempt to close the circuit
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    reset();
                    logger.info("Circuit breaker for {} transitioned from HALF_OPEN to CLOSED", serviceName);
                    stateTransitionTime.set(System.currentTimeMillis());
                }
            }
        }

        /**
         * Handle a failed execution.
         */
        private void onFailure() {
            slidingWindow.recordFailure();
            failureCount.incrementAndGet();
            lastFailureTime.set(System.currentTimeMillis());

            State currentState = state.get();

            if (currentState == State.HALF_OPEN) {
                // Failure in HALF_OPEN state, immediately open the circuit
                if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                    logger.warn("Circuit breaker for {} transitioned from HALF_OPEN to OPEN due to failure", serviceName);
                    stateTransitionTime.set(System.currentTimeMillis());
                }
            } else if (currentState == State.CLOSED) {
                // Check whether the circuit should be opened
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
         * Determine whether the circuit should be opened.
         */
        private boolean shouldOpenCircuit() {
            int totalRequests = slidingWindow.getTotalRequests();

            // Do not open the circuit if the request count is too low
            if (totalRequests < DEFAULT_MINIMUM_REQUESTS) {
                return false;
            }

            // Check the failure rate
            double failureRate = slidingWindow.getFailureRate();
            boolean shouldOpen = failureRate >= DEFAULT_FAILURE_RATE_THRESHOLD;

            if (shouldOpen) {
                logger.debug("Circuit breaker conditions met for {}: totalRequests={}, failureRate={:.2f}%, threshold={:.2f}%",
                        serviceName, totalRequests, failureRate * 100, DEFAULT_FAILURE_RATE_THRESHOLD * 100);
            }

            return shouldOpen;
        }

        /**
         * Determine whether a reset attempt should be made.
         */
        private boolean shouldAttemptReset() {
            long currentTime = System.currentTimeMillis();
            long timeSinceStateTransition = currentTime - stateTransitionTime.get();
            return timeSinceStateTransition >= DEFAULT_WAIT_DURATION.toMillis();
        }

        /**
         * Reset the circuit breaker.
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
         * Get the status of this circuit breaker.
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
     * Implementation of sliding window statistics.
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
         * Record a successful call.
         */
        public void recordSuccess() {
            record(true);
        }

        /**
         * Record a failed call.
         */
        public void recordFailure() {
            record(false);
        }

        /**
         * Record the result of a request.
         */
        private void record(boolean success) {
            synchronized (lock) {
                int index = currentIndex.getAndIncrement() % windowSize;

                // If the window is full, remove the oldest record
                if (totalRequests.get() >= windowSize) {
                    boolean oldValue = window[index];
                    if (!oldValue) { // the old record was a failure
                        failureRequests.decrementAndGet();
                    }
                } else {
                    totalRequests.incrementAndGet();
                }

                // Add the new record
                window[index] = success;
                if (!success) {
                    failureRequests.incrementAndGet();
                }
            }
        }

        /**
         * Get the failure rate.
         */
        public double getFailureRate() {
            int total = totalRequests.get();
            if (total == 0) {
                return 0.0;
            }
            return (double) failureRequests.get() / total;
        }

        /**
         * Get the total number of requests.
         */
        public int getTotalRequests() {
            return totalRequests.get();
        }

        /**
         * Get the number of failed requests.
         */
        public int getFailureRequests() {
            return failureRequests.get();
        }

        /**
         * Reset the window.
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
     * Circuit breaker state enumeration.
     */
    public enum State {
        CLOSED,    // Closed state, process requests normally
        OPEN,      // Open state, reject all requests
        HALF_OPEN  // Half-open state, allow a small number of test requests
    }

    /**
     * Circuit breaker status information.
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
     * Exception thrown when the circuit breaker is open.
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}