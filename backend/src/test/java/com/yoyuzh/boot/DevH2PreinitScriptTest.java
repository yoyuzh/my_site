package com.yoyuzh.boot;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DevH2PreinitScriptTest {

    @Test
    void backfillsLegacyPortalUserColumnsBeforeHibernateUpgrade() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:dev_h2_preinit_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        )) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE portal_user (
                            id BIGINT PRIMARY KEY,
                            created_at TIMESTAMP NOT NULL,
                            email VARCHAR(128) NOT NULL,
                            password_hash VARCHAR(255) NOT NULL,
                            username VARCHAR(64) NOT NULL,
                            last_school_semester VARCHAR(32),
                            last_school_student_id VARCHAR(64),
                            active_session_id VARCHAR(64),
                            avatar_content_type VARCHAR(128),
                            avatar_storage_name VARCHAR(255),
                            avatar_updated_at TIMESTAMP,
                            bio VARCHAR(280),
                            desktop_active_session_id VARCHAR(64),
                            max_upload_size_bytes BIGINT,
                            mobile_active_session_id VARCHAR(64),
                            phone_number VARCHAR(32),
                            storage_quota_bytes BIGINT
                        )
                        """);
                statement.execute("""
                        INSERT INTO portal_user (
                            id,
                            created_at,
                            email,
                            password_hash,
                            username
                        ) VALUES (
                            1,
                            CURRENT_TIMESTAMP,
                            'dev@example.com',
                            'hash',
                            'dev-user'
                        )
                        """);
            }

            ScriptUtils.executeSqlScript(
                    connection,
                    new EncodedResource(new ClassPathResource("dev-h2-preinit.sql")),
                    true,
                    false,
                    ScriptUtils.DEFAULT_COMMENT_PREFIX,
                    ScriptUtils.DEFAULT_STATEMENT_SEPARATOR,
                    ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER,
                    ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER
            );

            try (Statement statement = connection.createStatement();
                 ResultSet columns = statement.executeQuery("""
                         SELECT COLUMN_NAME
                         FROM INFORMATION_SCHEMA.COLUMNS
                         WHERE TABLE_NAME = 'PORTAL_USER'
                           AND COLUMN_NAME IN ('DISPLAY_NAME', 'PREFERRED_LANGUAGE', 'ROLE', 'BANNED')
                         ORDER BY COLUMN_NAME
                         """)) {
                StringBuilder actualColumns = new StringBuilder();
                while (columns.next()) {
                    if (!actualColumns.isEmpty()) {
                        actualColumns.append(",");
                    }
                    actualColumns.append(columns.getString(1));
                }
                assertEquals("BANNED,DISPLAY_NAME,PREFERRED_LANGUAGE,ROLE", actualColumns.toString());
            }

            try (Statement statement = connection.createStatement();
                 ResultSet user = statement.executeQuery("""
                         SELECT display_name, preferred_language, role, banned
                         FROM portal_user
                         WHERE id = 1
                         """)) {
                user.next();
                assertEquals("dev-user", user.getString("display_name"));
                assertEquals("zh-CN", user.getString("preferred_language"));
                assertEquals("USER", user.getString("role"));
                assertEquals(false, user.getBoolean("banned"));
            }
        }
    }

    @Test
    void backfillsLegacyPortalFileColumnsBeforeBackfillQueriesRun() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:dev_h2_preinit_portal_file_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        )) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE portal_file (
                            id BIGINT PRIMARY KEY,
                            content_type VARCHAR(255),
                            created_at TIMESTAMP NOT NULL,
                            is_directory BOOLEAN NOT NULL,
                            filename VARCHAR(255) NOT NULL,
                            path VARCHAR(512) NOT NULL,
                            size BIGINT NOT NULL,
                            storage_name VARCHAR(255) NOT NULL,
                            user_id BIGINT NOT NULL,
                            bucket VARCHAR(255),
                            etag VARCHAR(255),
                            object_key VARCHAR(255),
                            storage_provider VARCHAR(64),
                            deleted_at TIMESTAMP,
                            recycle_group_id VARCHAR(64),
                            recycle_original_path VARCHAR(512),
                            updated_at TIMESTAMP,
                            blob_id BIGINT,
                            primary_entity_id BIGINT
                        )
                        """);
                statement.execute("""
                        INSERT INTO portal_file (
                            id,
                            created_at,
                            is_directory,
                            filename,
                            path,
                            size,
                            storage_name,
                            user_id
                        ) VALUES (
                            1,
                            CURRENT_TIMESTAMP,
                            FALSE,
                            'demo.txt',
                            '/',
                            123,
                            'legacy-key',
                            1
                        )
                        """);
            }

            executeDevPreinitScript(connection);

            try (Statement statement = connection.createStatement();
                 ResultSet columns = statement.executeQuery("""
                         SELECT COLUMN_NAME
                         FROM INFORMATION_SCHEMA.COLUMNS
                         WHERE TABLE_NAME = 'PORTAL_FILE'
                           AND COLUMN_NAME = 'IS_RECYCLE_ROOT'
                         """)) {
                columns.next();
                assertEquals("IS_RECYCLE_ROOT", columns.getString(1));
            }

            try (Statement statement = connection.createStatement();
                 ResultSet file = statement.executeQuery("""
                         SELECT is_recycle_root
                         FROM portal_file
                         WHERE id = 1
                         """)) {
                file.next();
                assertEquals(false, file.getBoolean("is_recycle_root"));
            }
        }
    }

    @Test
    void ignoresLegacyBackfillStatementsWhenPortalUserTableDoesNotExistYet() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:dev_h2_preinit_missing_table_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        )) {
            executeDevPreinitScript(connection);
        }
    }

    @Test
    void createsAdminAuditLogTableForDevH2() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:dev_h2_preinit_audit_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        )) {
            executeDevPreinitScript(connection);

            try (Statement statement = connection.createStatement();
                 ResultSet columns = statement.executeQuery("""
                         SELECT COLUMN_NAME
                         FROM INFORMATION_SCHEMA.COLUMNS
                         WHERE TABLE_NAME = 'PORTAL_ADMIN_AUDIT_LOG'
                           AND COLUMN_NAME IN ('ACTOR_USERNAME', 'ACTION_TYPE', 'DETAILS_JSON', 'CREATED_AT')
                         ORDER BY COLUMN_NAME
                         """)) {
                StringBuilder actualColumns = new StringBuilder();
                while (columns.next()) {
                    if (!actualColumns.isEmpty()) {
                        actualColumns.append(",");
                    }
                    actualColumns.append(columns.getString(1));
                }
                assertEquals("ACTION_TYPE,ACTOR_USERNAME,CREATED_AT,DETAILS_JSON", actualColumns.toString());
            }
        }
    }

    private static void executeDevPreinitScript(Connection connection) throws Exception {
        ScriptUtils.executeSqlScript(
                connection,
                new EncodedResource(new ClassPathResource("dev-h2-preinit.sql")),
                true,
                false,
                ScriptUtils.DEFAULT_COMMENT_PREFIX,
                ScriptUtils.DEFAULT_STATEMENT_SEPARATOR,
                ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER,
                ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER
        );
    }
}
