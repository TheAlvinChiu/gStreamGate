package io.github.alvinchiu.gstreamgate.config;

import io.github.alvinchiu.gstreamgate.security.JwtAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Enhanced Spring Security configuration with JWT authentication and role-based access control.
 * 修復循環依賴問題
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    private final Environment environment;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(Environment environment, JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.environment = environment;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        logger.info("Configuring Enhanced Security Filter Chain with strict JWT authentication");

        // 檢查是否為開發環境
        boolean isDevelopment = environment.matchesProfiles("development");

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> {
                    logger.info("Setting up strict authorization rules with role-based access control");

                    // 公開端點 - 僅認證相關
                    authz.requestMatchers("/api/auth/login", "/api/auth/register").permitAll();

                    // 基本健康檢查 - 無需認證
                    authz.requestMatchers("/actuator/health").permitAll();

                    // H2 Console - 僅在開發環境開放
                    if (isDevelopment) {
                        authz.requestMatchers("/h2-console/**").permitAll();
                        logger.info("H2 Console access enabled for development environment");
                    }

                    // 管理員專用端點 - 需要ADMIN角色
                    authz.requestMatchers("/actuator/**").hasRole("ADMIN");
                    authz.requestMatchers("/api/proxy/refresh").hasRole("ADMIN");
                    authz.requestMatchers("/api/proxy/*/status").hasRole("ADMIN");
                    authz.requestMatchers("/api/proxy", "/api/proxy/enabled").hasAnyRole("USER", "ADMIN");
                    authz.requestMatchers("/api/proxy/*").hasRole("ADMIN"); // 個別代理操作需要管理員權限

                    // 需要認證的用戶端點
                    authz.requestMatchers("/api/auth/logout", "/api/auth/validate", "/api/auth/me").authenticated();

                    // 代理管理端點 - 需要認證
                    authz.requestMatchers("/api/proxy/test", "/api/proxy/health", "/api/proxy/active").authenticated();

                    // 所有其他請求都需要認證
                    authz.anyRequest().authenticated();
                })
                .headers(headers -> {
                    logger.info("Configuring enhanced security headers");
                    headers
                            .frameOptions(frameOptions -> frameOptions.sameOrigin())
                            .contentTypeOptions(contentTypeOptions -> {})
                            .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                                    .maxAgeInSeconds(31536000)
                                    .includeSubDomains(true))
                            .referrerPolicy(referrerPolicy ->
                                    referrerPolicy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        logger.info("Enhanced Security Filter Chain configured successfully with strict JWT authentication and role-based access control");
        return http.build();
    }
}