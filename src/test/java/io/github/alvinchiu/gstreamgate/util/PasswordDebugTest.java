package io.github.alvinchiu.gstreamgate.util;

import io.github.alvinchiu.gstreamgate.entity.User;
import io.github.alvinchiu.gstreamgate.repository.UserRepository;
import io.github.alvinchiu.gstreamgate.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

@SpringBootTest
@ActiveProfiles("development")
public class PasswordDebugTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Test
    public void debugAuthenticationFlow() {
        System.out.println("=== 認證流程調試 ===");

        // 1. 檢查資料庫中的用戶
        List<User> users = userRepository.findAll();
        System.out.println("資料庫中的用戶數量: " + users.size());

        for (User user : users) {
            System.out.println("用戶: " + user.getUsername());
            System.out.println("密碼雜湊: " + user.getPassword());
            System.out.println("郵箱: " + user.getEmail());
            System.out.println("角色: " + user.getRole());
            System.out.println("啟用狀態: " + user.isEnabled());

            // 測試密碼匹配
            boolean matches = passwordEncoder.matches("password", user.getPassword());
            System.out.println("密碼 'password' 匹配結果: " + matches);
            System.out.println("---");
        }

        // 2. 測試 UserDetailsService
        try {
            System.out.println("=== 測試 UserDetailsService ===");
            var userDetails = authService.loadUserByUsername("admin");
            System.out.println("UserDetails 載入成功: " + userDetails.getUsername());
            System.out.println("UserDetails 權限: " + userDetails.getAuthorities());
            System.out.println("UserDetails 啟用: " + userDetails.isEnabled());
        } catch (Exception e) {
            System.out.println("UserDetailsService 錯誤: " + e.getMessage());
        }

        // 3. 測試登入
        try {
            System.out.println("=== 測試登入流程 ===");
            var result = authService.login("admin", "password");
            System.out.println("登入成功: " + result);
        } catch (Exception e) {
            System.out.println("登入失敗: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    public void createCorrectTestUser() {
        System.out.println("=== 創建正確的測試用戶 ===");

        // 刪除現有用戶
        userRepository.findByUsername("admin").ifPresent(userRepository::delete);

        // 創建新用戶
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("password"));
        admin.setEmail("admin@example.com");
        admin.setRole(User.Role.ADMIN);
        admin.setEnabled(true);

        userRepository.save(admin);

        System.out.println("✅ 創建了新的 admin 用戶");
        System.out.println("密碼雜湊: " + admin.getPassword());

        // 驗證
        boolean matches = passwordEncoder.matches("password", admin.getPassword());
        System.out.println("密碼驗證: " + matches);

        // 測試登入
        try {
            var result = authService.login("admin", "password");
            System.out.println("登入測試成功: " + result.get("username"));
        } catch (Exception e) {
            System.out.println("登入測試失敗: " + e.getMessage());
        }
    }
}