CREATE TABLE IF NOT EXISTS portal_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    email VARCHAR(128) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS portal_file (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES portal_user (id),
    filename VARCHAR(255) NOT NULL,
    path VARCHAR(512) NOT NULL,
    storage_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    size BIGINT NOT NULL,
    is_directory BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_portal_file_user_path_name UNIQUE (user_id, path, filename)
);

CREATE INDEX IF NOT EXISTS idx_user_created_at ON portal_user (created_at);
CREATE INDEX IF NOT EXISTS idx_file_created_at ON portal_file (created_at);
