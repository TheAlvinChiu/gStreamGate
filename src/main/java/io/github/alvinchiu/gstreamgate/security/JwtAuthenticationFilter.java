package io.github.alvinchiu.gstreamgate.security;

import io.github.alvinchiu.gstreamgate.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final ApplicationContext applicationContext;

    // 使用 Lazy 方式避免循環依賴
    private AuthService authService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, ApplicationContext applicationContext) {
        this.jwtUtil = jwtUtil;
        this.applicationContext = applicationContext;
    }

    private AuthService getAuthService() {
        if (authService == null) {
            authService = applicationContext.getBean(AuthService.class);
        }
        return authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authorizationHeader = request.getHeader("Authorization");

        String username = null;
        String jwt = null;

        // 提取JWT token
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                username = jwtUtil.extractUsername(jwt);
            } catch (Exception e) {
                logger.error("Error extracting username from JWT: {}", e.getMessage());
            }
        }

        // 驗證token並設定authentication
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                AuthService authSvc = getAuthService();

                // 檢查token是否被列入黑名單
                if (authSvc.isTokenBlacklisted(jwt)) {
                    logger.warn("Attempt to use blacklisted token for user: {}", username);
                    filterChain.doFilter(request, response);
                    return;
                }

                UserDetails userDetails = authSvc.loadUserByUsername(username);

                if (jwtUtil.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    logger.debug("Successfully authenticated user: {}", username);
                }
            } catch (Exception e) {
                logger.error("Error during JWT authentication for user {}: {}", username, e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}