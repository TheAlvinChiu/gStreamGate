package io.github.alvinchiu.gstreamgate.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * OpenTelemetry configuration for distributed tracing
 * Supports OTLP and logging exporters
 */
@Configuration
public class OpenTelemetryConfig {
    private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryConfig.class);

    @Value("${opentelemetry.service.name:gstream-gate-proxy}")
    private String serviceName;

    @Value("${opentelemetry.service.version:${spring.application.version:1.0.0}}")
    private String serviceVersion;


    @Value("${opentelemetry.exporter.otlp.endpoint:http://localhost:4317}")
    private String otlpEndpoint;

    @Value("${opentelemetry.exporter.type:logging}")
    private String exporterType;

    @Value("${opentelemetry.traces.enabled:true}")
    private boolean tracesEnabled;

    @Value("${opentelemetry.sampler.probability:1.0}")
    private double samplerProbability;

    /**
     * Configure OpenTelemetry SDK
     */
    @Bean
    public OpenTelemetry openTelemetry() {
        // Temporarily return noop implementation until dependencies are resolved
        logger.info("OpenTelemetry tracing is temporarily disabled (dependency issues)");
        return OpenTelemetry.noop();
    }

    /**
     * Create span exporter based on configuration
     */
    private SpanExporter createSpanExporter() {
        switch (exporterType.toLowerCase()) {
            case "otlp":
                logger.info("Using OTLP exporter: {}", otlpEndpoint);
                return OtlpGrpcSpanExporter.builder()
                        .setEndpoint(otlpEndpoint)
                        .setTimeout(Duration.ofSeconds(30))
                        .build();

            case "logging":
            default:
                logger.info("Using logging exporter");
                return LoggingSpanExporter.create();
        }
    }

    /**
     * Get service instance ID
     */
    private String getInstanceId() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isEmpty()) {
            return hostname;
        }
        
        String podName = System.getenv("POD_NAME");
        if (podName != null && !podName.isEmpty()) {
            return podName;
        }
        
        return "unknown-instance";
    }

    /**
     * Get deployment environment
     */
    private String getEnvironment() {
        String env = System.getenv("DEPLOYMENT_ENV");
        if (env != null && !env.isEmpty()) {
            return env;
        }
        
        String profile = System.getProperty("spring.profiles.active");
        if (profile != null && !profile.isEmpty()) {
            return profile;
        }
        
        return "development";
    }
}