package com.minierp.platform.persistence;

import java.net.URI;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Fails before Flyway unless bootstrap tests use an empty dedicated database.
 * Dung truoc Flyway neu bootstrap test khong dung database rieng dang trong.
 */
public final class IsolatedBootstrapTestDatabaseSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String DATABASE_NAME = "mini_erp_isolated_test";
    private static final String ROLE_NAME = "mini_erp_isolated_app";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var environment = context.getEnvironment();
        String url = environment.getRequiredProperty("spring.datasource.url");
        String username = environment.getRequiredProperty("spring.datasource.username");
        String password = environment.getRequiredProperty("spring.datasource.password");

        verifyConfiguredTarget(url, username);
        verifyDatabaseIdentityAndEmptyApplicationState(url, username, password);
    }

    private void verifyConfiguredTarget(String url, String username) {
        URI databaseUri = URI.create(url.substring("jdbc:".length()));
        if (!("/" + DATABASE_NAME).equals(databaseUri.getPath()) || !ROLE_NAME.equals(username)) {
            throw new IllegalStateException("Bootstrap integration tests require " + DATABASE_NAME
                    + " as " + ROLE_NAME + ".");
        }
    }

    private void verifyDatabaseIdentityAndEmptyApplicationState(String url, String username, String password) {
        try (var connection = DriverManager.getConnection(url, username, password);
                var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("select current_database(), current_user")) {
                if (!result.next() || !DATABASE_NAME.equals(result.getString(1)) || !ROLE_NAME.equals(result.getString(2))) {
                    throw new IllegalStateException("Bootstrap integration test database identity verification failed.");
                }
            }

            if (tableExists(connection, "users") && applicationRecordsExist(connection)) {
                throw new IllegalStateException("Bootstrap integration test database is not empty; refusing to run.");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Bootstrap integration test database verification failed.", exception);
        }
    }

    private boolean tableExists(java.sql.Connection connection, String tableName) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select exists (
                    select 1 from information_schema.tables
                    where table_schema = 'public' and table_name = ?
                )
                """)) {
            statement.setString(1, tableName);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private boolean applicationRecordsExist(java.sql.Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        select exists (select 1 from users)
                            or exists (select 1 from user_roles)
                            or exists (select 1 from security_audit_records)
                        """)) {
            result.next();
            return result.getBoolean(1);
        }
    }
}
