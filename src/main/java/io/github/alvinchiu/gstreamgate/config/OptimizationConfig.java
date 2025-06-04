package io.github.alvinchiu.gstreamgate.config;

import io.github.alvinchiu.gstreamgate.adaptive.AdaptiveTimeoutManager;
import io.github.alvinchiu.gstreamgate.adaptive.SmartFlowControlManager;
import io.github.alvinchiu.gstreamgate.circuit.CircuitBreakerManager;
import io.github.alvinchiu.gstreamgate.handler.OptimizedProxyServerCallHandler;
import io.github.alvinchiu.gstreamgate.metrics.ProxyMetrics;
import io.github.alvinchiu.gstreamgate.optimization.MemoryOptimizer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * Optimization components configuration class.
 * Configures and initializes all performance optimization components.
 */
@Configuration
public class OptimizationConfig {
    private static final Logger logger = LoggerFactory.getLogger(OptimizationConfig.class);

    /**
     * Configure the {@link MeterRegistry} if no other registry is defined.
     */
    @Bean
    @ConditionalOnMissingBean
    public MeterRegistry meterRegistry() {
        logger.info("Creating default SimpleMeterRegistry for metrics");
        return new SimpleMeterRegistry();
    }

    /**
     * Configure the connection pool manager.
     */
    @Bean
    public io.github.alvinchiu.gstreamgate.pool.ConnectionPoolManager connectionPoolManager() {
        logger.info("Creating ConnectionPoolManager");
        return new io.github.alvinchiu.gstreamgate.pool.ConnectionPoolManager();
    }

    /**
     * Configure the circuit breaker manager.
     */
    @Bean
    public CircuitBreakerManager circuitBreakerManager() {
        logger.info("Creating CircuitBreakerManager");
        return new CircuitBreakerManager();
    }

    /**
     * Configure the metrics collector.
     */
    @Bean
    public ProxyMetrics proxyMetrics(MeterRegistry meterRegistry) {
        logger.info("Creating ProxyMetrics with registry: {}", meterRegistry.getClass().getSimpleName());
        return new ProxyMetrics(meterRegistry);
    }

    /**
     * Configure the memory optimizer.
     */
    @Bean
    public MemoryOptimizer memoryOptimizer() {
        logger.info("Creating MemoryOptimizer");
        return new MemoryOptimizer();
    }

    /**
     * Component injector - inject optimization components after the Spring
     * context is refreshed.
     */
    @EventListener
    public void onContextRefreshed(ContextRefreshedEvent event) {
        logger.info("Injecting optimization components into handlers");

        // Retrieve all optimization components
        CircuitBreakerManager circuitBreakerManager = event.getApplicationContext().getBean(CircuitBreakerManager.class);
        ProxyMetrics proxyMetrics = event.getApplicationContext().getBean(ProxyMetrics.class);
        MemoryOptimizer memoryOptimizer = event.getApplicationContext().getBean(MemoryOptimizer.class);

        // Attempt to retrieve adaptive components (may not exist)
        AdaptiveTimeoutManager timeoutManager = null;
        SmartFlowControlManager flowControlManager = null;

        try {
            timeoutManager = event.getApplicationContext().getBean(AdaptiveTimeoutManager.class);
            flowControlManager = event.getApplicationContext().getBean(SmartFlowControlManager.class);
            logger.info("Found adaptive components, will inject them as well");
        } catch (Exception e) {
            logger.info("Adaptive components not found, will use basic optimization only");
        }

        // Inject into the optimized handlers
        OptimizedProxyServerCallHandler.injectOptimizedComponents(
                circuitBreakerManager,
                proxyMetrics,
                memoryOptimizer,
                timeoutManager,
                flowControlManager
        );

        logger.info("Optimization components successfully injected");
    }
}