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
 * Spring Security 配置
 * 允許訪問 Actuator 監控端點，同時保護其他端點
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
                            // 允許訪問所有 Actuator 端點（開發/測試環境）
                            .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
                            // 明確允許常用的 actuator 路徑
                            .requestMatchers("/actuator/**").permitAll()
                            // 允許訪問 H2 控制台（開發環境）
                            .requestMatchers("/h2-console/**").permitAll()
                            // 允許訪問 API 端點（根據需要調整）
                            .requestMatchers("/api/**").permitAll()
                            // 其他所有請求需要認證
                            .anyRequest().authenticated();
                })
                // 禁用 CSRF（對於 API 和監控端點）
                .csrf(csrf -> {
                    logger.info("Disabling CSRF for API and actuator endpoints");
                    csrf
                            .ignoringRequestMatchers("/actuator/**")
                            .ignoringRequestMatchers("/h2-console/**")
                            .ignoringRequestMatchers("/api/**");
                })
                // 配置 Headers - 使用新的 API
                .headers(headers -> {
                    logger.info("Configuring security headers");
                    headers
                            // 允許 H2 控制台在 iframe 中顯示 - 使用新的 API
                            .frameOptions(frameOptions -> frameOptions.sameOrigin())
                            // 可選：添加其他安全頭
                            .contentTypeOptions(contentTypeOptions -> {})
                            .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                                    .maxAgeInSeconds(31536000)
                                    .includeSubDomains(true))
                            .referrerPolicy(referrerPolicy ->
                                    referrerPolicy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                })
                // 禁用基本認證（完全開放 actuator 端點）
                .httpBasic(httpBasic -> httpBasic.disable());

        logger.info("Security Filter Chain configured successfully");
        return http.build();
    }
}