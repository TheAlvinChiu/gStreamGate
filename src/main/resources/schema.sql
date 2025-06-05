CREATE TABLE IF NOT EXISTS grpc_proxy_map (
    proxy_map_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_name           VARCHAR(255) NOT NULL,                     -- Target service name
    proxy_hostname         VARCHAR(255) NOT NULL,                     -- Proxy location
    target_hostname        VARCHAR(255) NOT NULL,                     -- Target service location
    target_port            INT NOT NULL,                              -- Target service port
    connect_timeout_ms     INT NOT NULL    DEFAULT 5000,              -- Connection timeout ms
    send_timeout_ms        INT NOT NULL    DEFAULT 10000,             -- Send timeout ms
    read_timeout_ms        INT NOT NULL    DEFAULT 30000,             -- Read timeout ms
    secure_mode            VARCHAR(10)     DEFAULT 'AUTO',            -- TLS secure mode (AUTO, SECURE, PLAINTEXT)
    server_cert_content    TEXT,                                      -- X509 certificate content (PEM format)
    server_key_content     TEXT,                                      -- Private key content (PEM format)
    auto_trust_upstream_certs VARCHAR(1)   DEFAULT 'N',              -- Whether to auto-trust upstream certificates
    trusted_certs_content  TEXT,                                      -- Trusted CA certificates content (PEM format)
    enable                 VARCHAR(1)      DEFAULT 'N',               -- Enable/Disable
    create_date_time       DATETIME        DEFAULT CURRENT_TIMESTAMP, -- Creation date
    create_user            VARCHAR(100)    DEFAULT 'SYSTEM',          -- Creation user
    update_date_time       DATETIME,                                  -- Update date
    update_user            VARCHAR(100),                              -- Update user
    version                INT             DEFAULT 1                  -- Version
    );

CREATE TABLE IF NOT EXISTS users (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    username               VARCHAR(50) UNIQUE NOT NULL,               -- 用戶名
    password               VARCHAR(255) NOT NULL,                     -- 加密密碼
    email                  VARCHAR(100) UNIQUE NOT NULL,              -- 電子郵件
    role                   VARCHAR(20) DEFAULT 'USER',                -- 角色 (USER, ADMIN)
    enabled                BOOLEAN DEFAULT true,                      -- 是否啟用
    created_date           DATETIME DEFAULT CURRENT_TIMESTAMP,        -- 創建時間
    last_login             DATETIME                                   -- 最後登入時間
    );

-- grpc_proxy_map 索引
CREATE INDEX IF NOT EXISTS idx_proxy_hostname ON grpc_proxy_map(proxy_hostname);
CREATE INDEX IF NOT EXISTS idx_enable ON grpc_proxy_map(enable);

-- users 索引
CREATE INDEX IF NOT EXISTS idx_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_enabled ON users(enabled);