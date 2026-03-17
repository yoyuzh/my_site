CREATE DATABASE IF NOT EXISTS yoyuzh_portal DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE yoyuzh_portal;

CREATE TABLE IF NOT EXISTS portal_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(128) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_school_student_id VARCHAR(64),
    last_school_semester VARCHAR(64),
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

CREATE TABLE IF NOT EXISTS portal_course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    course_name VARCHAR(255) NOT NULL,
    semester VARCHAR(64),
    student_id VARCHAR(64),
    teacher VARCHAR(255),
    classroom VARCHAR(255),
    day_of_week INT,
    start_time INT,
    end_time INT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_portal_course_user FOREIGN KEY (user_id) REFERENCES portal_user (id)
);

CREATE TABLE IF NOT EXISTS portal_grade (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    course_name VARCHAR(255) NOT NULL,
    grade DOUBLE NOT NULL,
    semester VARCHAR(64) NOT NULL,
    student_id VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_portal_grade_user FOREIGN KEY (user_id) REFERENCES portal_user (id)
);

CREATE INDEX idx_user_created_at ON portal_user (created_at);
CREATE INDEX idx_file_created_at ON portal_file (created_at);
CREATE INDEX idx_course_user_semester ON portal_course (user_id, semester, student_id);
CREATE INDEX idx_course_user_created ON portal_course (user_id, created_at);
CREATE INDEX idx_grade_user_semester ON portal_grade (user_id, semester, student_id);
CREATE INDEX idx_grade_user_created ON portal_grade (user_id, created_at);
