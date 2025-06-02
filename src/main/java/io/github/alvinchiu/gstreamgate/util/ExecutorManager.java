package io.github.alvinchiu.gstreamgate.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 安全的執行器管理器
 * 確保執行器的正確生命週期管理
 */
@Component
public class ExecutorManager {
    private static final Logger logger = LoggerFactory.getLogger(ExecutorManager.class);

    private final ScheduledExecutorService healthCheckExecutor;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    public ExecutorManager() {
        // 創建帶有自定義線程工廠的執行器
        this.healthCheckExecutor = Executors.newScheduledThreadPool(2, new ThreadFactory() {
            private int threadNumber = 1;

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "grpc-proxy-health-check-" + threadNumber++);
                t.setDaemon(true); // 設置為守護線程
                t.setUncaughtExceptionHandler((thread, ex) ->
                        logger.error("Uncaught exception in thread {}: {}", thread.getName(), ex.getMessage(), ex));
                return t;
            }
        });

        logger.info("ExecutorManager initialized with health check executor");
    }

    /**
     * 獲取健康檢查執行器
     */
    public ScheduledExecutorService getHealthCheckExecutor() {
        if (isShutdown.get()) {
            logger.warn("Attempting to get executor after shutdown");
            return null;
        }
        return healthCheckExecutor;
    }

    /**
     * 安全地調度任務
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
     * 安全地調度一次性任務
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
     * 檢查執行器是否可用
     */
    public boolean isAvailable() {
        return !isShutdown.get() && !healthCheckExecutor.isShutdown();
    }

    /**
     * 優雅關閉執行器
     */
    @PreDestroy
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            logger.info("Shutting down ExecutorManager...");

            if (healthCheckExecutor != null && !healthCheckExecutor.isShutdown()) {
                healthCheckExecutor.shutdown();

                try {
                    // 等待執行器終止
                    if (!healthCheckExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                        logger.warn("Executor did not terminate gracefully, forcing shutdown");
                        healthCheckExecutor.shutdownNow();

                        // 再等待一下強制關閉
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