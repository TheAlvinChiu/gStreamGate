package io.github.alvinchiu.gstreamgate.initializer;

import io.github.alvinchiu.gstreamgate.adaptive.AdaptiveTimeoutManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Initializes adaptive managers at startup
 */
@Component
public class AdaptiveManagerInitializer implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveManagerInitializer.class);
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    // Inject AdaptiveTimeoutManager instance
    private final AdaptiveTimeoutManager timeoutManager;

    /**
     * Constructor injection for dependencies
     *
     * @param timeoutManager Adaptive timeout manager instance
     */
    public AdaptiveManagerInitializer(AdaptiveTimeoutManager timeoutManager) {
        this.timeoutManager = timeoutManager;
    }

    @Override
    public void run(String... args) {
        logger.info("Initializing adaptive managers");

        // Clean up stale statistics every 24 hours
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                logger.info("Cleaning up stale method statistics");
                timeoutManager.cleanupStaleMethodStats(); // Use injected instance
            } catch (Exception e) {
                logger.error("Error cleaning up stale method statistics: " + e.getMessage(), e);
            }
        }, 24, 24, TimeUnit.HOURS);

        // Gracefully shutdown scheduler on application close
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down adaptive manager scheduler");
            scheduledExecutor.shutdownNow();
        }));

        logger.info("Adaptive managers initialized successfully");
    }
}