package io.github.alvinchiu.gstreamgate.controller;

import io.github.alvinchiu.gstreamgate.entity.User;
import io.github.alvinchiu.gstreamgate.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 臨時密碼重置控制器
 * 僅在開發環境啟用
 */
@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "development")
public class DevPasswordResetController {
    private static final Logger logger = LoggerFactory.getLogger(DevPasswordResetController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 重置用戶密碼（僅開發環境）
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @RequestParam String username,
            @RequestParam String newPassword) {

        logger.info("開發環境密碼重置請求: {}", username);

        Map<String, Object> response = new HashMap<>();

        try {
            User user = userRepository.findByUsername(username).orElse(null);

            if (user == null) {
                response.put("success", false);
                response.put("message", "用戶不存在: " + username);
                return ResponseEntity.badRequest().body(response);
            }

            // 重置密碼
            String encodedPassword = passwordEncoder.encode(newPassword);
            user.setPassword(encodedPassword);
            userRepository.save(user);

            logger.info("密碼已重置: {}", username);

            response.put("success", true);
            response.put("message", "密碼重置成功");
            response.put("username", username);
            response.put("newPasswordHash", encodedPassword);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("密碼重置失敗: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "密碼重置失敗: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 創建測試用戶
     */
    @PostMapping("/create-user")
    public ResponseEntity<Map<String, Object>> createUser(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String email,
            @RequestParam(defaultValue = "USER") String role) {

        logger.info("創建測試用戶: {}", username);

        Map<String, Object> response = new HashMap<>();

        try {
            // 檢查用戶是否已存在
            if (userRepository.existsByUsername(username)) {
                // 刪除現有用戶
                userRepository.findByUsername(username).ifPresent(userRepository::delete);
                logger.info("已刪除現有用戶: {}", username);
            }

            // 創建新用戶
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));
            user.setEmail(email);
            user.setRole("ADMIN".equals(role) ? User.Role.ADMIN : User.Role.USER);
            user.setEnabled(true);

            userRepository.save(user);

            logger.info("用戶創建成功: {} (角色: {})", username, role);

            response.put("success", true);
            response.put("message", "用戶創建成功");
            response.put("username", username);
            response.put("role", role);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("用戶創建失敗: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "用戶創建失敗: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 檢查密碼
     */
    @PostMapping("/check-password")
    public ResponseEntity<Map<String, Object>> checkPassword(
            @RequestParam String username,
            @RequestParam String password) {

        Map<String, Object> response = new HashMap<>();

        try {
            User user = userRepository.findByUsername(username).orElse(null);

            if (user == null) {
                response.put("success", false);
                response.put("message", "用戶不存在");
                return ResponseEntity.badRequest().body(response);
            }

            boolean matches = passwordEncoder.matches(password, user.getPassword());

            response.put("success", matches);
            response.put("username", username);
            response.put("passwordMatches", matches);
            response.put("storedHash", user.getPassword());

            if (matches) {
                response.put("message", "密碼正確");
            } else {
                response.put("message", "密碼錯誤");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("密碼檢查失敗: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "密碼檢查失敗: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}