-- ==========================================
-- gStreamGate 資料庫架構 (H2 相容)
-- ==========================================

-- 刪除現有表格（如果存在）
DROP TABLE IF EXISTS grpc_proxy_map;
DROP TABLE IF EXISTS users;

-- 建立 gRPC 代理對應表
CREATE TABLE grpc_proxy_map (
                                proxy_map_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                                service_name           VARCHAR(255) NOT NULL,                     -- 目標服務名稱
                                proxy_hostname         VARCHAR(255) NOT NULL,                     -- 代理位置
                                target_hostname        VARCHAR(255) NOT NULL,                     -- 目標服務位置
                                target_port            INT NOT NULL,                              -- 目標服務埠號
                                connect_timeout_ms     INT NOT NULL    DEFAULT 5000,              -- 連線逾時 (毫秒)
                                send_timeout_ms        INT NOT NULL    DEFAULT 10000,             -- 傳送逾時 (毫秒)
                                read_timeout_ms        INT NOT NULL    DEFAULT 30000,             -- 讀取逾時 (毫秒)
                                secure_mode            VARCHAR(10)     DEFAULT 'AUTO',            -- TLS 安全模式 (AUTO, SECURE, PLAINTEXT)
                                server_cert_content    CLOB,                                      -- X509 憑證內容 (PEM 格式)
                                server_key_content     CLOB,                                      -- 私鑰內容 (PEM 格式)
                                auto_trust_upstream_certs VARCHAR(1)   DEFAULT 'N',              -- 是否自動信任上游憑證
                                trusted_certs_content  CLOB,                                      -- 受信任的 CA 憑證內容 (PEM 格式)
                                enable                 VARCHAR(1)      DEFAULT 'N',               -- 啟用/停用
                                create_date_time       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP, -- 建立日期
                                create_user            VARCHAR(100)    DEFAULT 'SYSTEM',          -- 建立使用者
                                update_date_time       TIMESTAMP,                                 -- 更新日期
                                update_user            VARCHAR(100),                              -- 更新使用者
                                version                INT             DEFAULT 1                  -- 版本號
);

-- 建立使用者表
CREATE TABLE users (
                       id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
                       username               VARCHAR(50) UNIQUE NOT NULL,               -- 使用者名稱
                       password               VARCHAR(255) NOT NULL,                     -- 加密密碼
                       email                  VARCHAR(100) UNIQUE NOT NULL,              -- 電子郵件
                       role                   VARCHAR(20) DEFAULT 'USER',                -- 角色 (USER, ADMIN)
                       enabled                BOOLEAN DEFAULT true,                      -- 是否啟用
                       created_date           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,       -- 建立時間
                       last_login             TIMESTAMP                                  -- 最後登入時間
);

-- 建立索引以提升查詢效能
CREATE INDEX IF NOT EXISTS idx_proxy_hostname ON grpc_proxy_map(proxy_hostname);
CREATE INDEX IF NOT EXISTS idx_enable ON grpc_proxy_map(enable);
CREATE INDEX IF NOT EXISTS idx_target_hostname ON grpc_proxy_map(target_hostname);

CREATE INDEX IF NOT EXISTS idx_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_enabled ON users(enabled);

-- 新增一些約束條件
ALTER TABLE grpc_proxy_map
    ADD CONSTRAINT chk_enable CHECK (enable IN ('Y', 'N'));

ALTER TABLE grpc_proxy_map
    ADD CONSTRAINT chk_secure_mode CHECK (secure_mode IN ('AUTO', 'SECURE', 'PLAINTEXT'));

ALTER TABLE grpc_proxy_map
    ADD CONSTRAINT chk_auto_trust CHECK (auto_trust_upstream_certs IN ('Y', 'N'));

ALTER TABLE users
    ADD CONSTRAINT chk_role CHECK (role IN ('USER', 'ADMIN'));

-- 新增註解說明
COMMENT ON TABLE grpc_proxy_map IS 'gRPC 代理服務對應表';
COMMENT ON TABLE users IS '系統使用者表';

COMMENT ON COLUMN grpc_proxy_map.proxy_hostname IS '代理主機名稱（客戶端連接的位址）';
COMMENT ON COLUMN grpc_proxy_map.target_hostname IS '目標主機名稱（實際服務位址）';
COMMENT ON COLUMN grpc_proxy_map.secure_mode IS 'TLS 模式：AUTO=自動偵測，SECURE=強制TLS，PLAINTEXT=明文';
COMMENT ON COLUMN users.role IS '使用者角色：USER=一般使用者，ADMIN=管理員';