-- ==========================================
-- gRPC Proxy Database Initialization Script - Updated Version
-- For PostgreSQL production environment, fully compatible with Spring Boot 3.5.0
-- ==========================================

-- Set basic parameters
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

-- ==========================================
-- Create extensions
-- ==========================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- ==========================================
-- Create gRPC proxy mapping main table
-- ==========================================
CREATE TABLE IF NOT EXISTS grpc_proxy_map (
    proxy_map_id           BIGSERIAL PRIMARY KEY,
    service_name           VARCHAR(255) NOT NULL,                     -- Target service name
    proxy_hostname         VARCHAR(255) NOT NULL,                     -- Proxy hostname
    target_hostname        VARCHAR(255) NOT NULL,                     -- Target service hostname
    target_port            INTEGER NOT NULL,                          -- Target service port
    connect_timeout_ms     INTEGER NOT NULL    DEFAULT 5000,          -- Connection timeout in milliseconds
    send_timeout_ms        INTEGER NOT NULL    DEFAULT 10000,         -- Send timeout in milliseconds
    read_timeout_ms        INTEGER NOT NULL    DEFAULT 30000,         -- Read timeout in milliseconds
    secure_mode            VARCHAR(10)         DEFAULT 'AUTO',        -- TLS security mode (AUTO, SECURE, PLAINTEXT)
    server_cert_content    TEXT,                                      -- X509 certificate content (PEM format)
    server_key_content     TEXT,                                      -- Private key content (PEM format)
    auto_trust_upstream_certs VARCHAR(1)       DEFAULT 'N',          -- Whether to auto-trust upstream certificates
    trusted_certs_content  TEXT,                                      -- Trusted CA certificates content (PEM format)
    enable                 VARCHAR(1)          DEFAULT 'N',           -- Enable/Disable
    create_date_time       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Creation date
    create_user            VARCHAR(100)        DEFAULT 'SYSTEM',      -- Creation user
    update_date_time       TIMESTAMP WITH TIME ZONE,                  -- Update date
    update_user            VARCHAR(100),                              -- Update user
    version                BIGINT              DEFAULT 1              -- Version number (JPA @Version compatible)
    );

-- ==========================================
-- Create user authentication table (Spring Security compatible)
-- ==========================================
CREATE TABLE IF NOT EXISTS users (
    id                     BIGSERIAL PRIMARY KEY,
    username               VARCHAR(50) UNIQUE NOT NULL,               -- Username
    password               VARCHAR(255) NOT NULL,                     -- BCrypt encrypted password
    email                  VARCHAR(100) UNIQUE NOT NULL,              -- Email address
    role                   VARCHAR(20) DEFAULT 'USER',                -- Role (USER, ADMIN)
    enabled                BOOLEAN DEFAULT true,                      -- Whether enabled
    created_date           TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Creation time
    last_login             TIMESTAMP WITH TIME ZONE                   -- Last login time
    );

-- ==========================================
-- Create indexes - Optimize query performance
-- ==========================================

-- grpc_proxy_map table indexes
CREATE INDEX IF NOT EXISTS idx_grpc_proxy_hostname ON grpc_proxy_map(proxy_hostname);
CREATE INDEX IF NOT EXISTS idx_grpc_proxy_enable ON grpc_proxy_map(enable);
CREATE INDEX IF NOT EXISTS idx_grpc_proxy_service_name ON grpc_proxy_map(service_name);
CREATE INDEX IF NOT EXISTS idx_grpc_proxy_target_host_port ON grpc_proxy_map(target_hostname, target_port);

-- Composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_grpc_proxy_enabled ON grpc_proxy_map(proxy_hostname, enable) WHERE enable = 'Y';
CREATE INDEX IF NOT EXISTS idx_grpc_proxy_service_enabled ON grpc_proxy_map(service_name, enable) WHERE enable = 'Y';

-- Timestamp indexes for auditing
CREATE INDEX IF NOT EXISTS idx_grpc_proxy_create_date ON grpc_proxy_map(create_date_time);
CREATE INDEX IF NOT EXISTS idx_grpc_proxy_update_date ON grpc_proxy_map(update_date_time);

-- users table indexes
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_enabled ON users(enabled);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_created_date ON users(created_date);
CREATE INDEX IF NOT EXISTS idx_users_last_login ON users(last_login);

-- ==========================================
-- Create constraints
-- ==========================================

-- grpc_proxy_map table constraints
ALTER TABLE grpc_proxy_map
    ADD CONSTRAINT IF NOT EXISTS uk_grpc_proxy_hostname UNIQUE (proxy_hostname);

-- Check constraints
ALTER TABLE grpc_proxy_map
    ADD CONSTRAINT IF NOT EXISTS chk_grpc_proxy_secure_mode
    CHECK (secure_mode IN ('AUTO', 'SECURE', 'PLAINTEXT'));

ALTER TABLE grpc_proxy_map
    ADD CONSTRAINT IF NOT EXISTS chk_grpc_proxy_enable
    CHECK (enable IN ('Y', 'N'));

ALTER TABLE grpc_proxy_map
    ADD CONSTRAINT IF NOT EXISTS chk_grpc_proxy_auto_trust
    CHECK (auto_trust_upstream_certs IN ('Y', 'N'));

ALTER TABLE grpc_proxy_map
    ADD CONSTRAINT IF NOT EXISTS chk_grpc_proxy_port_range
    CHECK (target_port > 0 AND target_port <= 65535);

ALTER TABLE grpc_proxy_map
    ADD CONSTRAINT IF NOT EXISTS chk_grpc_proxy_timeout_positive
    CHECK (connect_timeout_ms > 0 AND send_timeout_ms > 0 AND read_timeout_ms > 0);

-- users table constraints
ALTER TABLE users
    ADD CONSTRAINT IF NOT EXISTS chk_users_role
    CHECK (role IN ('USER', 'ADMIN'));

-- ==========================================
-- Create trigger functions
-- ==========================================

-- Update timestamp trigger function
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_date_time = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers
DROP TRIGGER IF EXISTS update_grpc_proxy_map_modtime ON grpc_proxy_map;
CREATE TRIGGER update_grpc_proxy_map_modtime
    BEFORE UPDATE ON grpc_proxy_map
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

-- ==========================================
-- Insert initial data
-- ==========================================

-- Clear existing data (optional)
-- TRUNCATE TABLE grpc_proxy_map RESTART IDENTITY CASCADE;
-- TRUNCATE TABLE users RESTART IDENTITY CASCADE;

-- Insert gRPC proxy mapping sample configurations
INSERT INTO grpc_proxy_map (
    service_name, proxy_hostname, target_hostname, target_port,
    connect_timeout_ms, send_timeout_ms, read_timeout_ms,
    secure_mode, enable, create_user
) VALUES
-- Test service configurations
('test-service', 'api.test.com', 'backend.test.com', 8080, 5000, 10000, 30000, 'AUTO', 'Y', 'SYSTEM'),
('user-service', 'users.api.com', 'users-backend.internal', 9090, 5000, 10000, 30000, 'PLAINTEXT', 'Y', 'SYSTEM'),

-- Production service configuration examples
('auth-service', 'auth.proxy.local', 'auth-service.internal', 50051, 5000, 10000, 30000, 'SECURE', 'Y', 'SYSTEM'),
('order-service', 'order.proxy.local', 'order-service.internal', 50052, 5000, 10000, 30000, 'SECURE', 'Y', 'SYSTEM'),
('payment-service', 'payment.proxy.local', 'payment-service.internal', 50053, 3000, 8000, 20000, 'SECURE', 'Y', 'SYSTEM'),
('notification-service', 'notification.proxy.local', 'notification-service.internal', 50054, 5000, 15000, 45000, 'AUTO', 'N', 'SYSTEM'),

-- Streaming service configurations
('streaming-service', 'stream.proxy.local', 'streaming-service.internal', 50055, 10000, 30000, 120000, 'SECURE', 'Y', 'SYSTEM'),
('chat-service', 'chat.proxy.local', 'chat-service.internal', 50056, 5000, 20000, 60000, 'SECURE', 'Y', 'SYSTEM'),

-- Example HTTP to gRPC service
('legacy-api', 'api.proxy.local', 'legacy-api.internal', 8080, 10000, 20000, 60000, 'PLAINTEXT', 'Y', 'SYSTEM')
    ON CONFLICT (proxy_hostname) DO NOTHING;

-- Insert user data (compatible with AuthService)
-- Password: password (BCrypt encrypted)
-- BCrypt hash: $2a$10$N9qo8uLOickgx2ZMRZoMye2J2KxvR4ZfETe5t0SgU0i5k0l8ZqKPC
INSERT INTO users (
    username, password, email, role, enabled, created_date
) VALUES
-- Admin users
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMye2J2KxvR4ZfETe5t0SgU0i5k0l8ZqKPC', 'admin@gstreamgate.com', 'ADMIN', true, CURRENT_TIMESTAMP),

-- Regular users
('user', '$2a$10$N9qo8uLOickgx2ZMRZoMye2J2KxvR4ZfETe5t0SgU0i5k0l8ZqKPC', 'user@gstreamgate.com', 'USER', true, CURRENT_TIMESTAMP),

-- Test users
('testuser', '$2a$10$N9qo8uLOickgx2ZMRZoMye2J2KxvR4ZfETe5t0SgU0i5k0l8ZqKPC', 'test@gstreamgate.com', 'USER', true, CURRENT_TIMESTAMP),

-- Development users
('dev', '$2a$10$N9qo8uLOickgx2ZMRZoMye2J2KxvR4ZfETe5t0SgU0i5k0l8ZqKPC', 'dev@gstreamgate.com', 'ADMIN', true, CURRENT_TIMESTAMP)
    ON CONFLICT (username) DO NOTHING;

-- ==========================================
-- Create performance monitoring views
-- ==========================================

-- Active proxy configuration view
CREATE OR REPLACE VIEW active_proxy_configs AS
SELECT
    proxy_map_id,
    service_name,
    proxy_hostname,
    target_hostname,
    target_port,
    secure_mode,
    create_date_time,
    update_date_time,
    create_user,
    update_user
FROM grpc_proxy_map
WHERE enable = 'Y'
ORDER BY service_name;

-- Proxy configuration statistics view
CREATE OR REPLACE VIEW proxy_config_stats AS
SELECT
    COUNT(*) as total_configs,
    COUNT(CASE WHEN enable = 'Y' THEN 1 END) as active_configs,
    COUNT(CASE WHEN enable = 'N' THEN 1 END) as inactive_configs,
    COUNT(CASE WHEN secure_mode = 'SECURE' THEN 1 END) as secure_configs,
    COUNT(CASE WHEN secure_mode = 'PLAINTEXT' THEN 1 END) as plaintext_configs,
    COUNT(CASE WHEN secure_mode = 'AUTO' THEN 1 END) as auto_configs
FROM grpc_proxy_map;

-- User statistics view
CREATE OR REPLACE VIEW user_stats AS
SELECT
    COUNT(*) as total_users,
    COUNT(CASE WHEN enabled = true THEN 1 END) as active_users,
    COUNT(CASE WHEN enabled = false THEN 1 END) as inactive_users,
    COUNT(CASE WHEN role = 'ADMIN' THEN 1 END) as admin_users,
    COUNT(CASE WHEN role = 'USER' THEN 1 END) as regular_users,
    COUNT(CASE WHEN last_login IS NOT NULL THEN 1 END) as users_with_login_history
FROM users;

-- ==========================================
-- Create audit tables (optional)
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

-- User operation audit table
CREATE TABLE IF NOT EXISTS user_audit_log (
    audit_id            BIGSERIAL PRIMARY KEY,
    user_id             BIGINT,
    operation_type      VARCHAR(20) NOT NULL,  -- LOGIN, LOGOUT, REGISTER, UPDATE, DELETE
    operation_details   JSONB,
    changed_by          VARCHAR(100),
    changed_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    client_ip           INET,
    user_agent          TEXT,
    session_id          VARCHAR(255)
    );

-- Audit log indexes
CREATE INDEX IF NOT EXISTS idx_grpc_proxy_audit_proxy_map_id ON grpc_proxy_audit_log(proxy_map_id);
CREATE INDEX IF NOT EXISTS idx_grpc_proxy_audit_operation_type ON grpc_proxy_audit_log(operation_type);
CREATE INDEX IF NOT EXISTS idx_grpc_proxy_audit_changed_at ON grpc_proxy_audit_log(changed_at);
CREATE INDEX IF NOT EXISTS idx_grpc_proxy_audit_changed_by ON grpc_proxy_audit_log(changed_by);

CREATE INDEX IF NOT EXISTS idx_user_audit_user_id ON user_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_user_audit_operation_type ON user_audit_log(operation_type);
CREATE INDEX IF NOT EXISTS idx_user_audit_changed_at ON user_audit_log(changed_at);
CREATE INDEX IF NOT EXISTS idx_user_audit_session_id ON user_audit_log(session_id);

-- ==========================================
-- Create audit trigger functions
-- ==========================================

-- gRPC proxy mapping audit trigger
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

-- User audit trigger
CREATE OR REPLACE FUNCTION log_user_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        INSERT INTO user_audit_log (user_id, operation_type, operation_details, changed_by)
        VALUES (OLD.id, 'DELETE', json_build_object('username', OLD.username, 'email', OLD.email), 'SYSTEM');
RETURN OLD;
ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO user_audit_log (user_id, operation_type, operation_details, changed_by)
        VALUES (NEW.id, 'UPDATE',
                json_build_object(
                    'old_values', row_to_json(OLD),
                    'new_values', row_to_json(NEW)
                ), 'SYSTEM');
RETURN NEW;
ELSIF TG_OP = 'INSERT' THEN
        INSERT INTO user_audit_log (user_id, operation_type, operation_details, changed_by)
        VALUES (NEW.id, 'REGISTER',
                json_build_object(
                    'username', NEW.username,
                    'email', NEW.email,
                    'role', NEW.role
                ), 'SYSTEM');
RETURN NEW;
END IF;
RETURN NULL;
END;
$$ language 'plpgsql';

-- Create audit triggers
DROP TRIGGER IF EXISTS audit_grpc_proxy_map ON grpc_proxy_map;
CREATE TRIGGER audit_grpc_proxy_map
    AFTER INSERT OR UPDATE OR DELETE ON grpc_proxy_map
    FOR EACH ROW
    EXECUTE FUNCTION log_proxy_changes();

DROP TRIGGER IF EXISTS audit_users ON users;
CREATE TRIGGER audit_users
    AFTER INSERT OR UPDATE OR DELETE ON users
    FOR EACH ROW
    EXECUTE FUNCTION log_user_changes();

-- ==========================================
-- Create useful functions
-- ==========================================

-- Get proxy configuration JSON
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

-- Health check function
CREATE OR REPLACE FUNCTION proxy_health_check()
RETURNS TABLE(
    service_name VARCHAR,
    proxy_hostname VARCHAR,
    target_hostname VARCHAR,
    target_port INTEGER,
    config_status TEXT,
    secure_mode VARCHAR
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
        END as config_status,
    p.secure_mode
FROM grpc_proxy_map p
ORDER BY p.service_name;
END;
$$ LANGUAGE plpgsql;

-- User statistics function
CREATE OR REPLACE FUNCTION get_user_statistics()
RETURNS JSON AS $$
BEGIN
RETURN (
    SELECT json_build_object(
                   'total_users', COUNT(*),
                   'active_users', COUNT(CASE WHEN enabled = true THEN 1 END),
                   'admin_users', COUNT(CASE WHEN role = 'ADMIN' THEN 1 END),
                   'recent_logins', COUNT(CASE WHEN last_login > CURRENT_TIMESTAMP - INTERVAL '7 days' THEN 1 END)
           )
    FROM users
);
END;
$$ LANGUAGE plpgsql;

-- JWT blacklist table (optional, for token revocation)
CREATE TABLE IF NOT EXISTS jwt_blacklist (
    id                  BIGSERIAL PRIMARY KEY,
    token_hash          VARCHAR(256) UNIQUE NOT NULL,     -- JWT token SHA-256 hash
    username            VARCHAR(50) NOT NULL,
    blacklisted_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    reason              VARCHAR(100) DEFAULT 'LOGOUT'
    );

-- JWT blacklist indexes
CREATE INDEX IF NOT EXISTS idx_jwt_blacklist_token_hash ON jwt_blacklist(token_hash);
CREATE INDEX IF NOT EXISTS idx_jwt_blacklist_username ON jwt_blacklist(username);
CREATE INDEX IF NOT EXISTS idx_jwt_blacklist_expires_at ON jwt_blacklist(expires_at);

-- Function to clean up expired JWT blacklist
CREATE OR REPLACE FUNCTION cleanup_expired_jwt_blacklist()
RETURNS INTEGER AS $$
DECLARE
deleted_count INTEGER;
BEGIN
DELETE FROM jwt_blacklist WHERE expires_at < CURRENT_TIMESTAMP;
GET DIAGNOSTICS deleted_count = ROW_COUNT;
RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- ==========================================
-- Permission settings (if specific user needed)
-- ==========================================

-- Grant permissions to application user (assuming username is gstreamgate)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'gstreamgate') THEN
        -- Main table permissions
        GRANT SELECT, INSERT, UPDATE, DELETE ON grpc_proxy_map TO gstreamgate;
GRANT USAGE, SELECT ON SEQUENCE grpc_proxy_map_proxy_map_id_seq TO gstreamgate;

GRANT SELECT, INSERT, UPDATE, DELETE ON users TO gstreamgate;
GRANT USAGE, SELECT ON SEQUENCE users_id_seq TO gstreamgate;

-- View permissions
GRANT SELECT ON active_proxy_configs TO gstreamgate;
GRANT SELECT ON proxy_config_stats TO gstreamgate;
GRANT SELECT ON user_stats TO gstreamgate;

-- Audit table permissions
GRANT SELECT, INSERT ON grpc_proxy_audit_log TO gstreamgate;
GRANT USAGE, SELECT ON SEQUENCE grpc_proxy_audit_log_audit_id_seq TO gstreamgate;

GRANT SELECT, INSERT ON user_audit_log TO gstreamgate;
GRANT USAGE, SELECT ON SEQUENCE user_audit_log_audit_id_seq TO gstreamgate;

-- JWT blacklist permissions
GRANT SELECT, INSERT, DELETE ON jwt_blacklist TO gstreamgate;
GRANT USAGE, SELECT ON SEQUENCE jwt_blacklist_id_seq TO gstreamgate;

-- Function execution permissions grpc_proxy_map_proxy_map_id_seq TO gstreamgate;

GRANT SELECT, INSERT, UPDATE, DELETE ON users TO gstreamgate;
GRANT USAGE, SELECT ON SEQUENCE users_id_seq TO gstreamgate;

-- 視圖權限
GRANT SELECT ON active_proxy_configs TO gstreamgate;
GRANT SELECT ON proxy_config_stats TO gstreamgate;
GRANT SELECT ON user_stats TO gstreamgate;

-- 審計表權限
GRANT SELECT, INSERT ON grpc_proxy_audit_log TO gstreamgate;
GRANT USAGE, SELECT ON SEQUENCE grpc_proxy_audit_log_audit_id_seq TO gstreamgate;

GRANT SELECT, INSERT ON user_audit_log TO gstreamgate;
GRANT USAGE, SELECT ON SEQUENCE user_audit_log_audit_id_seq TO gstreamgate;

-- JWT 黑名單權限
GRANT SELECT, INSERT, DELETE ON jwt_blacklist TO gstreamgate;
GRANT USAGE, SELECT ON SEQUENCE jwt_blacklist_id_seq TO gstreamgate;

-- 函數執行權限
GRANT EXECUTE ON FUNCTION get_proxy_config_json(VARCHAR) TO gstreamgate;
        GRANT EXECUTE ON FUNCTION proxy_health_check() TO gstreamgate;
        GRANT EXECUTE ON FUNCTION get_user_statistics() TO gstreamgate;
        GRANT EXECUTE ON FUNCTION cleanup_expired_jwt_blacklist() TO gstreamgate;

        RAISE NOTICE 'Permissions granted to gstreamgate user';
ELSE
        RAISE NOTICE 'User gstreamgate does not exist, skipping permission grants';
END IF;
END $$;

-- ==========================================
-- Performance optimization settings
-- ==========================================

-- Analyze table statistics
ANALYZE grpc_proxy_map;
ANALYZE users;

-- ==========================================
-- Periodic maintenance task settings (optional)
-- ==========================================

-- Create periodic task to clean up expired JWT (requires pg_cron extension)
-- SELECT cron.schedule('cleanup-jwt-blacklist', '0 2 * * *', 'SELECT cleanup_expired_jwt_blacklist();');

-- ==========================================
-- Completion information
-- ==========================================

DO $
BEGIN
    RAISE NOTICE '==========================================';
    RAISE NOTICE 'gRPC Proxy Database Initialization Complete!';
    RAISE NOTICE '==========================================';
    RAISE NOTICE '✅ Created main tables: grpc_proxy_map, users';
    RAISE NOTICE '✅ Created necessary indexes and constraints';
    RAISE NOTICE '✅ Set up audit logging functionality';
    RAISE NOTICE '✅ Inserted sample configuration data';
    RAISE NOTICE '✅ Inserted test user data (admin/user/testuser/dev, password: password)';
    RAISE NOTICE '✅ Created monitoring views and functions';
    RAISE NOTICE '✅ Created JWT blacklist support';
    RAISE NOTICE '✅ Set up permissions and performance optimization';
    RAISE NOTICE '==========================================';
    RAISE NOTICE 'Total proxy configurations: %', (SELECT COUNT(*) FROM grpc_proxy_map);
    RAISE NOTICE 'Enabled proxies: %', (SELECT COUNT(*) FROM grpc_proxy_map WHERE enable = 'Y');
    RAISE NOTICE 'Total users: %', (SELECT COUNT(*) FROM users);
    RAISE NOTICE 'Admin users: %', (SELECT COUNT(*) FROM users WHERE role = 'ADMIN');
    RAISE NOTICE '==========================================';
END $;