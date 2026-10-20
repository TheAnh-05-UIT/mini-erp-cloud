package com.minierp.platform.security.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minierp.platform.persistence.TestDatabaseSafetyInitializer;

/**
 * Verifies authentication JPA mappings and repository queries against Flyway V1. / Kiểm tra JPA mapping và repository của authentication với Flyway V1.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@Transactional
class AuthenticationPersistenceIntegrationTest {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SecurityAuditRecordRepository securityAuditRecordRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsNormalizedUserAndLoadsOnlyAssignedActiveRoles() {
        UserAccount user = persistUser("  Sales.Agent  ");
        Role salesRole = roleRepository.findByCode(RoleCode.SALES_STAFF).orElseThrow();
        userRoleRepository.saveAndFlush(UserRole.assign(user, salesRole, null));

        entityManager.clear();

        assertThat(userAccountRepository.findByUsername("sales.agent"))
                .isPresent()
                .get()
                .extracting(UserAccount::getUsername)
                .isEqualTo("sales.agent");

        UserAccount loaded = userAccountRepository.findActiveWithActiveRolesByUsername("sales.agent")
                .orElseThrow();

        assertThat(loaded.getRoleAssignments())
                .extracting(UserRole::getRole)
                .extracting(Role::getCode)
                .containsExactly(RoleCode.SALES_STAFF);
    }

    @Test
    void usesTheCompositeUserRoleKeyForLookup() {
        UserAccount user = persistUser("warehouse.agent");
        Role warehouseRole = roleRepository.findByCode(RoleCode.WAREHOUSE_STAFF).orElseThrow();
        UserRole assignment = userRoleRepository.saveAndFlush(UserRole.assign(user, warehouseRole, null));

        assertThat(userRoleRepository.findById(new UserRoleId(user.getId(), warehouseRole.getId())))
                .isPresent()
                .get()
                .extracting(UserRole::getId)
                .isEqualTo(assignment.getId());
    }

    @Test
    void persistsRedactedAuditMetadataAndNeverSerializesPasswordHash() throws Exception {
        UserAccount user = persistUser("audit.agent");
        SecurityAuditRecord record = SecurityAuditRecord.create(
                UUID.randomUUID(),
                user,
                null,
                "AUTHENTICATION_PERSISTENCE_TEST",
                SecurityAuditOutcome.SUCCESS,
                "test-user",
                "test-correlation",
                Map.of("source", "integration-test"));

        securityAuditRecordRepository.saveAndFlush(record);

        SecurityAuditRecord persistedRecord = securityAuditRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(persistedRecord.getMetadata()).isEqualTo(Map.of("source", "integration-test"));

        String serialized = objectMapper.writeValueAsString(user);
        assertThat(serialized).doesNotContain("passwordHash", "$2a$", "$2b$", "$2y$");
    }

    @Test
    void rejectsSensitiveAuditMetadataKeysBeforePersistence() {
        assertThatThrownBy(() -> SecurityAuditRecord.create(
                UUID.randomUUID(),
                null,
                null,
                "AUTHENTICATION_PERSISTENCE_TEST",
                SecurityAuditOutcome.FAILURE,
                null,
                null,
                Map.of("authorizationHeader", "redacted")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain secrets");
    }

    private UserAccount persistUser(String username) {
        UserAccount user = UserAccount.create(
                UUID.randomUUID(),
                username,
                PASSWORD_ENCODER.encode("test-only-password"),
                "Persistence Test User");
        return userAccountRepository.saveAndFlush(user);
    }
}
