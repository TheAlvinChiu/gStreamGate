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

-- 插入測試 gRPC 呼叫記錄資料
INSERT INTO grpc_call_logs (
    client_ip,
    target_location,
    method_name,
    execution_time_ms,
    status_code,
    request_size_bytes,
    response_size_bytes,
    error_message,
    trace_id,
    span_id,
    call_type,
    call_start_time,
    call_end_time,
    create_date_time
) VALUES
    (
        '192.168.1.100',
        'api.test.com:8080',
        '/test.TestService/GetUser',
        125,
        'OK',
        64,
        256,
        NULL,
        'a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6',
        'q1r2s3t4u5v6w7x8',
        'UNARY',
        DATEADD('HOUR', -2, CURRENT_TIMESTAMP),
        DATEADD('MILLISECOND', 125, DATEADD('HOUR', -2, CURRENT_TIMESTAMP)),
        DATEADD('HOUR', -2, CURRENT_TIMESTAMP)
    ),
    (
        '192.168.1.101',
        'users.api.com:9090',
        '/user.UserService/CreateUser',
        250,
        'OK',
        512,
        128,
        NULL,
        'b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7',
        'r2s3t4u5v6w7x8y9',
        'UNARY',
        DATEADD('HOUR', -1, CURRENT_TIMESTAMP),
        DATEADD('MILLISECOND', 250, DATEADD('HOUR', -1, CURRENT_TIMESTAMP)),
        DATEADD('HOUR', -1, CURRENT_TIMESTAMP)
    ),
    (
        '192.168.1.102',
        'secure.api.com:443',
        '/secure.SecureService/Authenticate',
        1500,
        'UNAUTHENTICATED',
        128,
        64,
        'Authentication failed: invalid credentials',
        'c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8',
        's3t4u5v6w7x8y9z0',
        'UNARY',
        DATEADD('MINUTE', -30, CURRENT_TIMESTAMP),
        DATEADD('MILLISECOND', 1500, DATEADD('MINUTE', -30, CURRENT_TIMESTAMP)),
        DATEADD('MINUTE', -30, CURRENT_TIMESTAMP)
    ),
    (
        '192.168.1.103',
        'api.test.com:8080',
        '/test.TestService/ListUsers',
        75,
        'OK',
        32,
        1024,
        NULL,
        'd4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9',
        't4u5v6w7x8y9z0a1',
        'SERVER_STREAMING',
        DATEADD('MINUTE', -15, CURRENT_TIMESTAMP),
        DATEADD('MILLISECOND', 75, DATEADD('MINUTE', -15, CURRENT_TIMESTAMP)),
        DATEADD('MINUTE', -15, CURRENT_TIMESTAMP)
    ),
    (
        '192.168.1.104',
        'users.api.com:9090',
        '/user.UserService/UpdateUser',
        300,
        'OK',
        256,
        128,
        NULL,
        'e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0',
        'u5v6w7x8y9z0a1b2',
        'UNARY',
        DATEADD('MINUTE', -5, CURRENT_TIMESTAMP),
        DATEADD('MILLISECOND', 300, DATEADD('MINUTE', -5, CURRENT_TIMESTAMP)),
        DATEADD('MINUTE', -5, CURRENT_TIMESTAMP)
    ),
    (
        '192.168.1.105',
        'api.test.com:8080',
        '/test.TestService/DeleteUser',
        3000,
        'INTERNAL',
        128,
        0,
        'Database connection timeout',
        'f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1',
        'v6w7x8y9z0a1b2c3',
        'UNARY',
        DATEADD('MINUTE', -2, CURRENT_TIMESTAMP),
        DATEADD('MILLISECOND', 3000, DATEADD('MINUTE', -2, CURRENT_TIMESTAMP)),
        DATEADD('MINUTE', -2, CURRENT_TIMESTAMP)
    ),
    (
        '192.168.1.106',
        'secure.api.com:443',
        '/secure.SecureService/GetSecureData',
        180,
        'OK',
        64,
        2048,
        NULL,
        'g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2',
        'w7x8y9z0a1b2c3d4',
        'UNARY',
        DATEADD('SECOND', -30, CURRENT_TIMESTAMP),
        DATEADD('MILLISECOND', 180, DATEADD('SECOND', -30, CURRENT_TIMESTAMP)),
        DATEADD('SECOND', -30, CURRENT_TIMESTAMP)
    ),
    (
        '192.168.1.107',
        'users.api.com:9090',
        '/user.UserService/StreamUpdates',
        5000,
        'DEADLINE_EXCEEDED',
        128,
        512,
        'Request timeout after 5000ms',
        'h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3',
        'x8y9z0a1b2c3d4e5',
        'BIDI_STREAMING',
        DATEADD('SECOND', -10, CURRENT_TIMESTAMP),
        DATEADD('MILLISECOND', 5000, DATEADD('SECOND', -10, CURRENT_TIMESTAMP)),
        DATEADD('SECOND', -10, CURRENT_TIMESTAMP)
    );

-- 驗證資料插入
-- SELECT COUNT(*) as proxy_count FROM grpc_proxy_map;
-- SELECT COUNT(*) as user_count FROM users;
-- SELECT COUNT(*) as grpc_log_count FROM grpc_call_logs;
-- SELECT username, email, role, enabled FROM users;