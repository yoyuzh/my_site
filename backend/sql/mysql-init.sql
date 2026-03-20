CREATE DATABASE IF NOT EXISTS yoyuzh_portal DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE yoyuzh_portal;

CREATE TABLE IF NOT EXISTS portal_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(128) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_portal_user_username UNIQUE (username),
    CONSTRAINT uk_portal_user_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS portal_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    filename VARCHAR(255) NOT NULL,
    path VARCHAR(512) NOT NULL,
    storage_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    size BIGINT NOT NULL,
    is_directory BIT NOT NULL DEFAULT b'0',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_portal_file_user FOREIGN KEY (user_id) REFERENCES portal_user (id),
    CONSTRAINT uk_portal_file_user_path_name UNIQUE (user_id, path, filename)
);

CREATE INDEX idx_user_created_at ON portal_user (created_at);
CREATE INDEX idx_file_created_at ON portal_file (created_at);
