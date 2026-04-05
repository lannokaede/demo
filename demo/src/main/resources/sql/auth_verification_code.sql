CREATE TABLE IF NOT EXISTS auth_verification_code (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    target_value VARCHAR(128) NOT NULL,
    code VARCHAR(16) NOT NULL,
    code_type VARCHAR(16) NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_auth_verification_target_type (target_value, code_type),
    KEY idx_auth_verification_expires_at (expires_at)
);
