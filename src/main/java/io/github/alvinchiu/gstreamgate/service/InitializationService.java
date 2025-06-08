package io.github.alvinchiu.gstreamgate.service;

import io.github.alvinchiu.gstreamgate.entity.User;
import io.github.alvinchiu.gstreamgate.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 系統初始化服務
 * 負責在應用程式啟動時建立預設管理者帳號
 */
@Service
public class InitializationService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(InitializationService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Environment environment;

    @Override
    public void run(String... args) throws Exception {
        logger.info("開始系統初始化...");
        
        initializeDefaultAdmin();
        
        // 如果是開發環境，也建立測試使用者
        if (environment.matchesProfiles("development")) {
            initializeTestUsers();
        }
        
        logger.info("系統初始化完成");
    }

    /**
     * 初始化預設管理者帳號
     */
    private void initializeDefaultAdmin() {
        String adminUsername = "admin";
        
        if (userRepository.existsByUsername(adminUsername)) {
            logger.info("管理者帳號已存在，跳過建立");
            return;
        }

        try {
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode("admin123"));  // 預設密碼
            admin.setEmail("admin@gstreamgate.local");
            admin.setRole(User.Role.ADMIN);
            admin.setEnabled(true);

            userRepository.save(admin);
            
            logger.info("預設管理者帳號建立成功");
            logger.info("管理者帳號: admin");
            logger.info("預設密碼: admin123");
            logger.warn("⚠️  請務必在首次登入後修改密碼！");
            
        } catch (Exception e) {
            logger.error("建立預設管理者帳號失敗: {}", e.getMessage(), e);
        }
    }

    /**
     * 初始化測試使用者（僅開發環境）
     */
    private void initializeTestUsers() {
        logger.info("開發環境：建立測試使用者");
        
        // 建立測試使用者
        if (!userRepository.existsByUsername("testuser")) {
            try {
                User testUser = new User();
                testUser.setUsername("testuser");
                testUser.setPassword(passwordEncoder.encode("test123"));
                testUser.setEmail("test@gstreamgate.local");
                testUser.setRole(User.Role.USER);
                testUser.setEnabled(true);

                userRepository.save(testUser);
                logger.info("測試使用者建立成功: testuser / test123");
                
            } catch (Exception e) {
                logger.error("建立測試使用者失敗: {}", e.getMessage(), e);
            }
        }
    }
}