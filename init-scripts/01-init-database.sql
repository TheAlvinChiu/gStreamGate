-- ==========================================
-- gRPC Proxy 數據庫初始化腳本
-- 為 PostgreSQL 生產環境準備
-- ==========================================

-- 設置基本參數
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

-- ==========================================
-- 創建擴展
-- ==========================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- ==========================================
-- 創建主表
-- ==========================================
CREATE TABLE IF NOT EXISTS grpc_proxy_map (
                                              proxy_map_id           BIGSERIAL PRIMARY KEY,
                                              service_name           VARCHAR(255) NOT NULL,                     -- 目標服務名稱
    proxy_hostname         VARCHAR(255) NOT NULL,                     -- 代理位置
    target_hostname        VARCHAR(255) NOT NULL,                     -- 目標服務位置
    target_port            INTEGER NOT NULL,                          -- 目標服務端口
    connect_timeout_ms     INTEGER NOT NULL    DEFAULT 5000,          -- 連接超時毫秒
    send_timeout_ms        INTEGER NOT NULL    DEFAULT 10000,         -- 發送超時毫秒
    read_timeout_ms        INTEGER NOT NULL    DEFAULT 30000,         -- 讀取超時毫秒
    secure_mode            VARCHAR(10)         DEFAULT 'AUTO',        -- TLS 安全模式 (AUTO, SECURE, PLAINTEXT)
    server_cert_content    TEXT,                                      -- X509 證書內容 (PEM 格式)
    server_key_content     TEXT,                                      -- 私鑰內容 (PEM 格式)
    auto_trust_upstream_certs VARCHAR(1)       DEFAULT 'N',          -- 是否自動信任上游證書
    trusted_certs_content  TEXT,                                      -- 可信 CA 證書內容 (PEM 格式)
    enable                 VARCHAR(1)          DEFAULT 'N',           -- 啟用/禁用
    create_date_time       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- 創建日期
                                         create_user            VARCHAR(100)        DEFAULT 'SYSTEM',      -- 創建用戶
    update_date_time       TIMESTAMP WITH TIME ZONE,                  -- 更新日期
                                         update_user            VARCHAR(100),                              -- 更新用戶
    version                INTEGER             DEFAULT 1              -- 版本號
    );

-- ==========================================
-- 創建索引
-- ==========================================

-- 主要查詢索引
CREATE INDEX IF NOT EXISTS idx_proxy_hostname ON grpc_proxy_map(proxy_hostname);
CREATE INDEX IF NOT EXISTS idx_enable ON grpc_proxy_map(enable);
CREATE INDEX IF NOT EXISTS idx_service_name ON grpc_proxy_map(service_name);
CREATE INDEX IF NOT EXISTS idx_target_host_port ON grpc_proxy_map(target_hostname, target_port);

-- 復合索引用於常見查詢
CREATE INDEX IF NOT EXISTS idx_proxy_enabled ON grpc_proxy_map(proxy_hostname, enable) WHERE enable = 'Y';
CREATE INDEX IF NOT EXISTS idx_service_enabled ON grpc_proxy_map(service_name, enable) WHERE enable = 'Y';

-- 時間戳索引用於審計
CREATE INDEX IF NOT EXISTS idx_create_date ON grpc_proxy_map(create_date_time);
CREATE INDEX IF NOT EXISTS idx_update_date ON grpc_proxy_map(update_date_time);

-- ==========================================
-- 創建約束
-- ==========================================

-- 唯一約束
ALTER TABLE grpc_proxy_map
    ADD CONSTRAINT IF NOT EXISTS uk_proxy_hostname UNIQUE (proxy_hostname);

-- 檢查約束
ALTER TABLE grpc_proxy_map
    ADD CONSTRAINT IF NOT EXISTS chk_secure_mode
    CHECK (secure_mode IN ('AUTO', 'SECURE', 'PLAINTEXT'));

ALTER TABLE grpc_proxy_map
    ADD CONSTRAINT IF NOT EXISTS chk_enable
    CHECK (enable IN ('Y', 'N'));

ALTER TABLE grpc_proxy_map
    ADD CONSTRAINT IF NOT EXISTS chk_auto_trust
    CHECK (auto_trust_upstream_certs IN ('Y', 'N'));

ALTER TABLE grpc_proxy_map
    ADD CONSTRAINT IF NOT EXISTS chk_port_range
    CHECK (target_port > 0 AND target_port <= 65535);

ALTER TABLE grpc_proxy_map
    ADD CONSTRAINT IF NOT EXISTS chk_timeout_positive
    CHECK (connect_timeout_ms > 0 AND send_timeout_ms > 0 AND read_timeout_ms > 0);

-- ==========================================
-- 創建觸發器函數
-- ==========================================

-- 更新時間戳觸發器函數
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_date_time = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

-- 創建觸發器
DROP TRIGGER IF EXISTS update_grpc_proxy_map_modtime ON grpc_proxy_map;
CREATE TRIGGER update_grpc_proxy_map_modtime
    BEFORE UPDATE ON grpc_proxy_map
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

-- ==========================================
-- 插入初始數據（示例）
-- ==========================================

-- 清除現有數據（可選）
-- TRUNCATE TABLE grpc_proxy_map RESTART IDENTITY;

-- 插入示例配置
INSERT INTO grpc_proxy_map (
    service_name, proxy_hostname, target_hostname, target_port,
    connect_timeout_ms, send_timeout_ms, read_timeout_ms,
    secure_mode, enable, create_user
) VALUES
-- 示例 gRPC 服務配置
('user-service', 'user.proxy.local', 'user-service.internal', 50051, 5000, 10000, 30000, 'AUTO', 'Y', 'SYSTEM'),
('order-service', 'order.proxy.local', 'order-service.internal', 50052, 5000, 10000, 30000, 'SECURE', 'Y', 'SYSTEM'),
('payment-service', 'payment.proxy.local', 'payment-service.internal', 50053, 3000, 8000, 20000, 'SECURE', 'Y', 'SYSTEM'),
('notification-service', 'notification.proxy.local', 'notification-service.internal', 50054, 5000, 15000, 45000, 'AUTO', 'N', 'SYSTEM'),
-- 示例 HTTP 轉 gRPC 服務
('legacy-api', 'api.proxy.local', 'legacy-api.internal', 8080, 10000, 20000, 60000, 'PLAINTEXT', 'Y', 'SYSTEM')
    ON CONFLICT (proxy_hostname) DO NOTHING;

-- ==========================================
-- 創建性能監控視圖
-- ==========================================

-- 活躍代理配置視圖
CREATE OR REPLACE VIEW active_proxy_configs AS
SELECT
    proxy_map_id,
    service_name,
    proxy_hostname,
    target_hostname,
    target_port,
    secure_mode,
    create_date_time,
    update_date_time
FROM grpc_proxy_map
WHERE enable = 'Y'
ORDER BY service_name;

-- 代理配置統計視圖
CREATE OR REPLACE VIEW proxy_config_stats AS
SELECT
    COUNT(*) as total_configs,
    COUNT(CASE WHEN enable = 'Y' THEN 1 END) as active_configs,
    COUNT(CASE WHEN enable = 'N' THEN 1 END) as inactive_configs,
    COUNT(CASE WHEN secure_mode = 'SECURE' THEN 1 END) as secure_configs,
    COUNT(CASE WHEN secure_mode = 'PLAINTEXT' THEN 1 END) as plaintext_configs,
    COUNT(CASE WHEN secure_mode = 'AUTO' THEN 1 END) as auto_configs
FROM grpc_proxy_map;

-- ==========================================
-- 創建審計表（可選）
-- ==========================================

CREATE TABLE IF NOT EXISTS grpc_proxy_audit_log (
                                                    audit_id            BIGSERIAL PRIMARY KEY,
                                                    proxy_map_id        BIGINT,
                                                    operation_type      VARCHAR(10) NOT NULL,  -- INSERT, UPDATE, DELETE
    old_values          JSONB,
    new_values          JSONB,
    changed_by          VARCHAR(100),
    changed_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                      client_ip           INET,
                                      user_agent          TEXT
                                      );

-- 審計日志索引
CREATE INDEX IF NOT EXISTS idx_audit_proxy_map_id ON grpc_proxy_audit_log(proxy_map_id);
CREATE INDEX IF NOT EXISTS idx_audit_operation_type ON grpc_proxy_audit_log(operation_type);
CREATE INDEX IF NOT EXISTS idx_audit_changed_at ON grpc_proxy_audit_log(changed_at);
CREATE INDEX IF NOT EXISTS idx_audit_changed_by ON grpc_proxy_audit_log(changed_by);

-- ==========================================
-- 創建審計觸發器函數
-- ==========================================

CREATE OR REPLACE FUNCTION log_proxy_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        INSERT INTO grpc_proxy_audit_log (proxy_map_id, operation_type, old_values, changed_by)
        VALUES (OLD.proxy_map_id, 'DELETE', row_to_json(OLD), OLD.update_user);
RETURN OLD;
ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO grpc_proxy_audit_log (proxy_map_id, operation_type, old_values, new_values, changed_by)
        VALUES (NEW.proxy_map_id, 'UPDATE', row_to_json(OLD), row_to_json(NEW), NEW.update_user);
RETURN NEW;
ELSIF TG_OP = 'INSERT' THEN
        INSERT INTO grpc_proxy_audit_log (proxy_map_id, operation_type, new_values, changed_by)
        VALUES (NEW.proxy_map_id, 'INSERT', row_to_json(NEW), NEW.create_user);
RETURN NEW;
END IF;
RETURN NULL;
END;
$$ language 'plpgsql';

-- 創建審計觸發器
DROP TRIGGER IF EXISTS audit_grpc_proxy_map ON grpc_proxy_map;
CREATE TRIGGER audit_grpc_proxy_map
    AFTER INSERT OR UPDATE OR DELETE ON grpc_proxy_map
    FOR EACH ROW
    EXECUTE FUNCTION log_proxy_changes();

-- ==========================================
-- 創建有用的函數
-- ==========================================

-- 獲取代理配置 JSON
CREATE OR REPLACE FUNCTION get_proxy_config_json(hostname VARCHAR)
RETURNS JSON AS $$
BEGIN
RETURN (
    SELECT row_to_json(t)
    FROM (
             SELECT service_name, proxy_hostname, target_hostname, target_port,
                    connect_timeout_ms, send_timeout_ms, read_timeout_ms,
                    secure_mode, enable
             FROM grpc_proxy_map
             WHERE proxy_hostname = hostname AND enable = 'Y'
         ) t
);
END;
$$ LANGUAGE plpgsql;

-- 健康檢查函數
CREATE OR REPLACE FUNCTION proxy_health_check()
RETURNS TABLE(
    service_name VARCHAR,
    proxy_hostname VARCHAR,
    target_hostname VARCHAR,
    target_port INTEGER,
    config_status TEXT
) AS $$
BEGIN
RETURN QUERY
SELECT
    p.service_name,
    p.proxy_hostname,
    p.target_hostname,
    p.target_port,
    CASE
        WHEN p.enable = 'Y' THEN 'ACTIVE'
        ELSE 'INACTIVE'
        END as config_status
FROM grpc_proxy_map p
ORDER BY p.service_name;
END;
$$ LANGUAGE plpgsql;

-- ==========================================
-- 權限設置（如果需要特定用戶）
-- ==========================================

-- 為應用程序用戶授權（假設用戶名為 gstreamgate）
GRANT SELECT, INSERT, UPDATE, DELETE ON grpc_proxy_map TO gstreamgate;
GRANT USAGE, SELECT ON SEQUENCE grpc_proxy_map_proxy_map_id_seq TO gstreamgate;
GRANT SELECT ON active_proxy_configs TO gstreamgate;
GRANT SELECT ON proxy_config_stats TO gstreamgate;
GRANT SELECT, INSERT ON grpc_proxy_audit_log TO gstreamgate;
GRANT USAGE, SELECT ON SEQUENCE grpc_proxy_audit_log_audit_id_seq TO gstreamgate;

-- ==========================================
-- 性能優化設置
-- ==========================================

-- 分析表統計信息
ANALYZE grpc_proxy_map;

-- 打印初始化完成信息
DO $$
BEGIN
    RAISE NOTICE 'gRPC Proxy 數據庫初始化完成!';
    RAISE NOTICE '- 創建了主表 grpc_proxy_map';
    RAISE NOTICE '- 創建了必要的索引和約束';
    RAISE NOTICE '- 設置了審計日志功能';
    RAISE NOTICE '- 插入了示例配置數據';
    RAISE NOTICE '- 創建了監控視圖和函數';
END $$;