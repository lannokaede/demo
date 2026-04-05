CREATE TABLE IF NOT EXISTS auth_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(32) NOT NULL,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(128) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_auth_user_user_id (user_id),
    UNIQUE KEY uk_auth_user_username (username),
    UNIQUE KEY uk_auth_user_phone (phone),
    UNIQUE KEY uk_auth_user_email (email)
);

ALTER TABLE auth_user ADD COLUMN IF NOT EXISTS phone VARCHAR(32) NULL AFTER nickname;
ALTER TABLE auth_user ADD COLUMN IF NOT EXISTS email VARCHAR(128) NULL AFTER phone;
ALTER TABLE auth_user ADD UNIQUE KEY IF NOT EXISTS uk_auth_user_phone (phone);
ALTER TABLE auth_user ADD UNIQUE KEY IF NOT EXISTS uk_auth_user_email (email);
