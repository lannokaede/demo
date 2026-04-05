CREATE TABLE IF NOT EXISTS wps_file (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    file_id VARCHAR(64) NOT NULL,
    bucket_name VARCHAR(128) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    file_name VARCHAR(240) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    size BIGINT NOT NULL DEFAULT 0,
    creator_id VARCHAR(64) NOT NULL,
    modifier_id VARCHAR(64) NOT NULL,
    create_time BIGINT NOT NULL,
    modify_time BIGINT NOT NULL,
    UNIQUE KEY uk_wps_file_file_id (file_id)
);
