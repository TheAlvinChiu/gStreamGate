-- ==========================================
-- gStreamGate 初始資料 (H2 相容)
-- ==========================================

-- 插入測試代理服務資料
INSERT INTO grpc_proxy_map (
    service_name,
    proxy_hostname,
    target_hostname,
    target_port,
    connect_timeout_ms,
    send_timeout_ms,
    read_timeout_ms,
    secure_mode,
    auto_trust_upstream_certs,
    enable,
    create_date_time,
    create_user,
    version
) VALUES
      (
          'test-service',
          'api.test.com',
          'backend.test.com',
          8080,
          5000,
          10000,
          30000,
          'AUTO',
          'N',
          'Y',
          CURRENT_TIMESTAMP,
          'SYSTEM',
          1
      ),
      (
          'user-service',
          'users.api.com',
          'users-backend.internal',
          9090,
          5000,
          10000,
          30000,
          'PLAINTEXT',
          'N',
          'Y',
          CURRENT_TIMESTAMP,
          'SYSTEM',
          1
      ),
      (
          'secure-api',
          'secure.api.com',
          'secure-backend.internal',
          443,
          5000,
          15000,
          45000,
          'SECURE',
          'N',
          'Y',
          CURRENT_TIMESTAMP,
          'SYSTEM',
          1
      );

-- 驗證資料插入
-- SELECT COUNT(*) as proxy_count FROM grpc_proxy_map;
-- SELECT COUNT(*) as user_count FROM users;
-- SELECT username, email, role, enabled FROM users;