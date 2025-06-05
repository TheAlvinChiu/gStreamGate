package io.github.alvinchiu.gstreamgate.service;

import io.github.alvinchiu.gstreamgate.entity.User;
import io.github.alvinchiu.gstreamgate.repository.UserRepository;
import io.github.alvinchiu.gstreamgate.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService implements UserDetailsService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ApplicationContext applicationContext;

    // 用於儲存已登出的token（簡單的黑名單機制）
    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();

    // Lazy loading for AuthenticationManager
    private AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       ApplicationContext applicationContext) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.applicationContext = applicationContext;
    }

    private AuthenticationManager getAuthenticationManager() {
        if (authenticationManager == null) {
            authenticationManager = applicationContext.getBean(AuthenticationManager.class);
        }
        return authenticationManager;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Transactional
    public Map<String, Object> login(String username, String password) {
        try {
            // 驗證使用者憑證
            Authentication authentication = getAuthenticationManager().authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            // 生成JWT token
            String token = jwtUtil.generateToken(userDetails);

            // 更新最後登入時間
            userRepository.updateLastLogin(username, new Date());

            logger.info("User logged in successfully: {}", username);

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("username", username);
            response.put("message", "Login successful");

            return response;

        } catch (Exception e) {
            logger.error("Login failed for user: {}, error: {}", username, e.getMessage());
            throw new RuntimeException("Invalid username or password");
        }
    }

    public Map<String, Object> logout(String token) {
        try {
            // 將token加入黑名單
            if (token != null && jwtUtil.isTokenValid(token)) {
                blacklistedTokens.add(token);

                String username = jwtUtil.extractUsername(token);
                logger.info("User logged out successfully: {}", username);

                Map<String, Object> response = new HashMap<>();
                response.put("message", "Logout successful");
                response.put("username", username);

                return response;
            } else {
                throw new RuntimeException("Invalid token");
            }
        } catch (Exception e) {
            logger.error("Logout failed: {}", e.getMessage());
            throw new RuntimeException("Logout failed");
        }
    }

    @Transactional
    public Map<String, Object> register(String username, String password, String email) {
        try {
            // 檢查使用者是否已存在
            if (userRepository.existsByUsername(username)) {
                throw new RuntimeException("Username already exists");
            }

            if (userRepository.existsByEmail(email)) {
                throw new RuntimeException("Email already exists");
            }

            // 創建新使用者
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));
            user.setEmail(email);
            user.setRole(User.Role.USER);
            user.setEnabled(true);

            userRepository.save(user);

            logger.info("User registered successfully: {}", username);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "User registered successfully");
            response.put("username", username);

            return response;

        } catch (Exception e) {
            logger.error("Registration failed for user: {}, error: {}", username, e.getMessage());
            throw e;
        }
    }

    public boolean isTokenBlacklisted(String token) {
        return blacklistedTokens.contains(token);
    }

    public Map<String, Object> validateToken(String token) {
        try {
            if (isTokenBlacklisted(token)) {
                throw new RuntimeException("Token has been revoked");
            }

            if (!jwtUtil.isTokenValid(token)) {
                throw new RuntimeException("Token is invalid or expired");
            }

            String username = jwtUtil.extractUsername(token);

            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("username", username);

            return response;

        } catch (Exception e) {
            logger.error("Token validation failed: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            response.put("error", e.getMessage());

            return response;
        }
    }
}