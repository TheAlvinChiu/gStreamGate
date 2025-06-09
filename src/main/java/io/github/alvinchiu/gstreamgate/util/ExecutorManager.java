package io.github.alvinchiu.gstreamgate.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Safe executor manager
 * Ensures proper executor lifecycle management
 */
@Component
public class ExecutorManager {
    private static final Logger logger = LoggerFactory.getLogger(ExecutorManager.class);

    private final ScheduledExecutorService healthCheckExecutor;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    public ExecutorManager() {
        // Create executor with optimized thread pool size
        this.healthCheckExecutor = Executors.newScheduledThreadPool(4, new ThreadFactory() {
            private int threadNumber = 1;

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "grpc-proxy-health-check-" + threadNumber++);
                t.setDaemon(true); // Set as daemon thread
                t.setUncaughtExceptionHandler((thread, ex) ->
                        logger.error("Uncaught exception in thread {}: {}", thread.getName(), ex.getMessage(), ex));
                return t;
            }
        });

        logger.info("ExecutorManager initialized with health check executor");
    }

    /**
     * Get the health check executor
     */
    public ScheduledExecutorService getHealthCheckExecutor() {
        if (isShutdown.get()) {
            logger.warn("Attempting to get executor after shutdown");
            return null;
        }
        return healthCheckExecutor;
    }

    /**
     * Safely schedule a recurring task
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        if (isShutdown.get()) {
            logger.warn("Cannot schedule task - executor is shutdown");
            return null;
        }

        try {
            return healthCheckExecutor.scheduleAtFixedRate(task, initialDelay, period, unit);
        } catch (RejectedExecutionException e) {
            logger.error("Task rejected by executor: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Safely schedule a one-off task
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        if (isShutdown.get()) {
            logger.warn("Cannot schedule task - executor is shutdown");
            return null;
        }

        try {
            return healthCheckExecutor.schedule(task, delay, unit);
        } catch (RejectedExecutionException e) {
            logger.error("Task rejected by executor: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check whether the executor is available
     */
    public boolean isAvailable() {
        return !isShutdown.get() && !healthCheckExecutor.isShutdown();
    }

    /**
     * Gracefully shut down the executor
     */
    @PreDestroy
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            logger.info("Shutting down ExecutorManager...");

            if (healthCheckExecutor != null && !healthCheckExecutor.isShutdown()) {
                healthCheckExecutor.shutdown();

                try {
                    // Wait for executor termination
                    if (!healthCheckExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                        logger.warn("Executor did not terminate gracefully, forcing shutdown");
                        healthCheckExecutor.shutdownNow();

                        // Wait again after forced shutdown
                        if (!healthCheckExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                            logger.error("Executor did not terminate after forced shutdown");
                        }
                    }
                    logger.info("ExecutorManager shut down successfully");
                } catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting for executor termination");
                    healthCheckExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}