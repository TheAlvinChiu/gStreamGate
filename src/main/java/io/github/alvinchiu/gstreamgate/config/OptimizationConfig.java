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
 * 優化組件配置類
 * 配置和初始化所有性能優化組件
 */
@Configuration
public class OptimizationConfig {
    private static final Logger logger = LoggerFactory.getLogger(OptimizationConfig.class);

    /**
     * 配置 MeterRegistry（如果沒有其他配置）
     */
    @Bean
    @ConditionalOnMissingBean
    public MeterRegistry meterRegistry() {
        logger.info("Creating default SimpleMeterRegistry for metrics");
        return new SimpleMeterRegistry();
    }

    /**
     * 配置連接池管理器
     */
    @Bean
    public io.github.alvinchiu.gstreamgate.pool.ConnectionPoolManager connectionPoolManager() {
        logger.info("Creating ConnectionPoolManager");
        return new io.github.alvinchiu.gstreamgate.pool.ConnectionPoolManager();
    }

    /**
     * 配置熔斷器管理器
     */
    @Bean
    public CircuitBreakerManager circuitBreakerManager() {
        logger.info("Creating CircuitBreakerManager");
        return new CircuitBreakerManager();
    }

    /**
     * 配置 Metrics 收集器
     */
    @Bean
    public ProxyMetrics proxyMetrics(MeterRegistry meterRegistry) {
        logger.info("Creating ProxyMetrics with registry: {}", meterRegistry.getClass().getSimpleName());
        return new ProxyMetrics(meterRegistry);
    }

    /**
     * 配置內存優化器
     */
    @Bean
    public MemoryOptimizer memoryOptimizer() {
        logger.info("Creating MemoryOptimizer");
        return new MemoryOptimizer();
    }

    /**
     * 組件注入器 - 在 Spring 上下文刷新後注入優化組件
     */
    @EventListener
    public void onContextRefreshed(ContextRefreshedEvent event) {
        logger.info("Injecting optimization components into handlers");

        // 獲取所有優化組件
        CircuitBreakerManager circuitBreakerManager = event.getApplicationContext().getBean(CircuitBreakerManager.class);
        ProxyMetrics proxyMetrics = event.getApplicationContext().getBean(ProxyMetrics.class);
        MemoryOptimizer memoryOptimizer = event.getApplicationContext().getBean(MemoryOptimizer.class);

        // 嘗試獲取自適應組件（可能不存在）
        AdaptiveTimeoutManager timeoutManager = null;
        SmartFlowControlManager flowControlManager = null;

        try {
            timeoutManager = event.getApplicationContext().getBean(AdaptiveTimeoutManager.class);
            flowControlManager = event.getApplicationContext().getBean(SmartFlowControlManager.class);
            logger.info("Found adaptive components, will inject them as well");
        } catch (Exception e) {
            logger.info("Adaptive components not found, will use basic optimization only");
        }

        // 注入到優化的處理器中
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