package io.github.alvinchiu.gstreamgate.initializer;

import io.github.alvinchiu.gstreamgate.adaptive.AdaptiveTimeoutManager;
import io.github.alvinchiu.gstreamgate.adaptive.SmartFlowControlManager;
import io.github.alvinchiu.gstreamgate.handler.ProxyServerCallHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * Proxy handler initializer
 * Injects adaptive manager instances into ProxyServerCallHandler when Spring context refreshes
 */
@Component
public class ProxyHandlerInitializer implements ApplicationListener<ContextRefreshedEvent> {
    private static final Logger logger = LoggerFactory.getLogger(ProxyHandlerInitializer.class);
    private final AdaptiveTimeoutManager timeoutManager;
    private final SmartFlowControlManager flowControlManager;

    /**
     * Constructor injection for dependencies
     *
     * @param timeoutManager Adaptive timeout manager
     * @param flowControlManager Smart flow control manager
     */
    public ProxyHandlerInitializer(
            AdaptiveTimeoutManager timeoutManager,
            SmartFlowControlManager flowControlManager) {
        this.timeoutManager = timeoutManager;
        this.flowControlManager = flowControlManager;
    }

    /**
     * Called when Spring context is refreshed
     *
     * @param event Context refresh event
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        logger.info("Injecting adaptive managers into ProxyServerCallHandler");

        // Inject manager instances into ProxyServerCallHandler
        ProxyServerCallHandler.injectManagers(timeoutManager, flowControlManager);

        logger.info("Adaptive managers successfully injected into proxy handler");
    }
}