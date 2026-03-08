package com.zhiwu.project2026.distributecache.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Auto-create required tables for distributecache when JDBC mode is enabled.
 */
@Component
@ConditionalOnProperty(prefix = "distributecache.repo.db", name = "type", havingValue = "jdbc")
public class DatabaseSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;
    private final DbInitProperties dbInitProperties;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate, DbInitProperties dbInitProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.dbInitProperties = dbInitProperties;
    }

    @PostConstruct
    public void initSchema() {
        if (!dbInitProperties.isEnabled()) {
            return;
        }

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS meas_object (
                oid INT PRIMARY KEY AUTO_INCREMENT,
                dn VARCHAR(255) NOT NULL,
                original_value VARCHAR(255) NOT NULL,
                display_value_zh VARCHAR(512),
                display_value_en VARCHAR(512),
                UNIQUE KEY uk_dn_original (dn, original_value),
                KEY idx_dn (dn),
                KEY idx_original_value (original_value)
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS task_oid_binding (
                task_key VARCHAR(128) NOT NULL,
                mo_type VARCHAR(128) NOT NULL,
                oid INT NOT NULL,
                PRIMARY KEY (task_key, mo_type, oid),
                KEY idx_oid (oid),
                CONSTRAINT fk_task_oid_meas
                    FOREIGN KEY (oid) REFERENCES meas_object (oid)
                    ON DELETE CASCADE
            )
            """);
    }
}
