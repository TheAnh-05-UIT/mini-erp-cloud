package com.minierp.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Verifies the V1 security schema, baseline roles, and database constraints. / Kiểm tra security schema V1, role nền tảng và database constraints.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
class SecuritySchemaIntegrationTest {

    private static final List<String> ROLE_CODES = List.of(
            "ADMIN",
            "PURCHASING_STAFF",
            "WAREHOUSE_STAFF",
            "SALES_STAFF",
            "MANAGER");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Test
    void appliesApprovedMigrationsAndSeedsEachBaselineRoleExactlyOnce() throws SQLException {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT code FROM roles ORDER BY code");
                ResultSet resultSet = statement.executeQuery()) {
            java.util.ArrayList<String> actualCodes = new java.util.ArrayList<>();
            while (resultSet.next()) {
                actualCodes.add(resultSet.getString("code"));
            }

            assertThat(actualCodes).containsExactlyInAnyOrderElementsOf(ROLE_CODES);
        }
    }

    @Test
    void createsApprovedSecurityAndCatalogTablesAndConstraints() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "users")).isTrue();
            assertThat(tableExists(connection, "roles")).isTrue();
            assertThat(tableExists(connection, "user_roles")).isTrue();
            assertThat(tableExists(connection, "security_audit_records")).isTrue();

            assertThat(tableExists(connection, "business_audit_records")).isTrue();
            assertThat(tableExists(connection, "categories")).isTrue();
            assertThat(tableExists(connection, "products")).isTrue();
            assertThat(tableExists(connection, "purchase_orders")).isFalse();
            assertThat(tableExists(connection, "sales_orders")).isFalse();
            assertThat(tableExists(connection, "inventory_stock")).isFalse();

            assertThat(constraintExists(connection, "ck_users_username_lowercase")).isTrue();
            assertThat(constraintExists(connection, "ck_users_username_trimmed")).isTrue();
            assertThat(constraintExists(connection, "ck_roles_code_allowed")).isTrue();
            assertThat(constraintExists(connection, "ck_security_audit_records_outcome")).isTrue();
            assertThat(constraintExists(connection, "pk_user_roles")).isTrue();
            assertThat(constraintExists(connection, "fk_user_roles_user")).isTrue();
            assertThat(constraintExists(connection, "fk_user_roles_role")).isTrue();
            assertThat(constraintExists(connection, "fk_security_audit_records_actor_user")).isTrue();
            assertThat(constraintExists(connection, "fk_security_audit_records_target_user")).isTrue();
            assertThat(constraintExists(connection, "ck_categories_code_canonical")).isTrue();
            assertThat(constraintExists(connection, "ck_categories_name_trimmed")).isTrue();
            assertThat(constraintExists(connection, "ck_products_sku_canonical")).isTrue();
            assertThat(constraintExists(connection, "ck_products_unit_of_measure")).isTrue();

            assertThat(indexExists(connection, "ux_users_username_normalized")).isTrue();
            assertThat(indexExists(connection, "ix_user_roles_role_id")).isTrue();
            assertThat(indexExists(connection, "ix_security_audit_records_occurred_at")).isTrue();
            assertThat(indexExists(connection, "ix_security_audit_records_actor_occurred_at")).isTrue();
            assertThat(indexExists(connection, "ix_security_audit_records_target_occurred_at")).isTrue();
            assertThat(indexExists(connection, "ix_security_audit_records_event_occurred_at")).isTrue();
            assertThat(indexExists(connection, "ux_categories_code")).isTrue();
            assertThat(indexExists(connection, "ux_categories_normalized_name")).isTrue();
            assertThat(indexExists(connection, "ux_products_sku")).isTrue();
        }
    }

    @Test
    void enforcesSecurityConstraintsWithoutPersistingTestData() {
        assertRejectedAndRolledBack("""
                INSERT INTO users (id, username, password_hash, display_name)
                VALUES ('10000000-0000-0000-0000-000000000001', 'UppercaseUser', 'test-password-hash', 'Schema Test User')
                """);
        assertRejectedAndRolledBack("""
                INSERT INTO users (id, username, password_hash, display_name)
                VALUES ('10000000-0000-0000-0000-000000000002', ' trailing', 'test-password-hash', 'Schema Test User')
                """);
        assertRejectedAndRolledBack("""
                INSERT INTO roles (id, code, name)
                VALUES ('10000000-0000-0000-0000-000000000003', 'UNAPPROVED_ROLE', 'Unapproved Role')
                """);
        assertRejectedAndRolledBack("""
                INSERT INTO security_audit_records (id, event_type, outcome)
                VALUES ('10000000-0000-0000-0000-000000000004', 'TEST_EVENT', 'UNKNOWN')
                """);
        assertDuplicateUsernameIsRejectedAndRolledBack();
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?)")) {
            statement.setString(1, "public." + tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getString(1) != null;
            }
        }
    }

    private boolean constraintExists(Connection connection, String constraintName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = ?)")) {
            statement.setString(1, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private boolean indexExists(Connection connection, String indexName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?)")) {
            statement.setString(1, "public." + indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getString(1) != null;
            }
        }
    }

    private void assertRejectedAndRolledBack(String sql) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);

            assertThatThrownBy(() -> statement.execute(sql)).isInstanceOf(SQLException.class);

            connection.rollback();
        } catch (SQLException exception) {
            throw new AssertionError("Constraint test setup failed.", exception);
        }
    }

    private void assertDuplicateUsernameIsRejectedAndRolledBack() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("""
                    INSERT INTO users (id, username, password_hash, display_name)
                    VALUES ('10000000-0000-0000-0000-000000000005', 'schema-test-user', 'test-password-hash', 'Schema Test User')
                    """);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO users (id, username, password_hash, display_name)
                    VALUES ('10000000-0000-0000-0000-000000000006', 'schema-test-user', 'test-password-hash', 'Duplicate Schema Test User')
                    """)).isInstanceOf(SQLException.class);

            connection.rollback();
        } catch (SQLException exception) {
            throw new AssertionError("Unique-constraint test setup failed.", exception);
        }
    }
}
