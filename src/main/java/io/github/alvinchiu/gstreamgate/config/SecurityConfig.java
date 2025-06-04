package io.github.alvinchiu.gstreamgate.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Spring Security configuration.
 * Allows access to Actuator monitoring endpoints while protecting others.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        logger.info("Configuring Security Filter Chain - allowing Actuator endpoints");

        http
                .authorizeHttpRequests(authz -> {
                    logger.info("Setting up authorization rules");
                    authz
                            // Allow access to all Actuator endpoints (development/test environments)
                            .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
                            // Explicitly allow common actuator paths
                            .requestMatchers("/actuator/**").permitAll()
                            // Allow access to the H2 console (development environment)
                            .requestMatchers("/h2-console/**").permitAll()
                            // Allow access to API endpoints (adjust as needed)
                            .requestMatchers("/api/**").permitAll()
                            // All other requests require authentication
                            .anyRequest().authenticated();
                })
                // Disable CSRF for API and actuator endpoints
                .csrf(csrf -> {
                    logger.info("Disabling CSRF for API and actuator endpoints");
                    csrf
                            .ignoringRequestMatchers("/actuator/**")
                            .ignoringRequestMatchers("/h2-console/**")
                            .ignoringRequestMatchers("/api/**");
                })
                // Configure headers using the new API
                .headers(headers -> {
                    logger.info("Configuring security headers");
                    headers
                            // Allow the H2 console to be displayed in an iframe - using the new API
                            .frameOptions(frameOptions -> frameOptions.sameOrigin())
                            // Optional: add additional security headers
                            .contentTypeOptions(contentTypeOptions -> {})
                            .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                                    .maxAgeInSeconds(31536000)
                                    .includeSubDomains(true))
                            .referrerPolicy(referrerPolicy ->
                                    referrerPolicy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                })
                // Disable basic authentication (actuator endpoints are fully open)
                .httpBasic(httpBasic -> httpBasic.disable());

        logger.info("Security Filter Chain configured successfully");
        return http.build();
    }
}