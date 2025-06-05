-- 插入測試數據（使用大寫表名）
INSERT INTO GRPC_PROXY_MAP (
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
      );

-- 插入測試用戶數據（使用正確的 BCrypt 雜湊）
-- 密碼: password
-- BCrypt 雜湊: $2a$10$N9qo8uLOickgx2ZMRZoMye2J2KxvR4ZfETe5t0SgU0i5k0l8ZqKPC
INSERT INTO USERS (
    username,
    password,
    email,
    role,
    enabled,
    created_date
) VALUES
      (
          'admin',
          '$2a$10$N9qo8uLOickgx2ZMRZoMye2J2KxvR4ZfETe5t0SgU0i5k0l8ZqKPC',
          'admin@example.com',
          'ADMIN',
          true,
          CURRENT_TIMESTAMP
      ),
      (
          'user',
          '$2a$10$N9qo8uLOickgx2ZMRZoMye2J2KxvR4ZfETe5t0SgU0i5k0l8ZqKPC',
          'user@example.com',
          'USER',
          true,
          CURRENT_TIMESTAMP
      );