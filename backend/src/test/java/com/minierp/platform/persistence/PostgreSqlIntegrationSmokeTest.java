package com.minierp.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Verifies the isolated PostgreSQL test database and Flyway baseline are usable. / Kiểm tra PostgreSQL test database độc lập và Flyway baseline có thể sử dụng.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
class PostgreSqlIntegrationSmokeTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Test
    void connectsToIsolatedPostgreSqlTestDatabaseAndAppliesAuthSecurityMigration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getCatalog()).isEqualTo("mini_erp_test");

            try (ResultSet resultSet = connection.createStatement()
                    .executeQuery("SELECT to_regclass('public.flyway_schema_history')")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("flyway_schema_history");
            }
        }

        assertThat(flyway.info().applied())
                .extracting(migrationInfo -> migrationInfo.getVersion().getVersion())
                .contains("1");
    }
}
