package com.minierp.platform.security.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.minierp.platform.persistence.TestDatabaseSafetyInitializer;
import com.minierp.platform.security.persistence.Role;
import com.minierp.platform.security.persistence.RoleCode;
import com.minierp.platform.security.persistence.RoleRepository;
import com.minierp.platform.security.persistence.SecurityAuditOutcome;
import com.minierp.platform.security.persistence.SecurityAuditRecordRepository;
import com.minierp.platform.security.persistence.UserAccount;
import com.minierp.platform.security.persistence.UserAccountRepository;

/**
 * Verifies one-time Admin bootstrap behavior with the isolated PostgreSQL test database. / Kiểm tra bootstrap Admin một lần với PostgreSQL test database độc lập.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@Transactional
class AdminBootstrapServiceIntegrationTest {

    @Autowired
    private AdminBootstrapService adminBootstrapService;

    @Autowired
    private AdminBootstrapProperties properties;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SecurityAuditRecordRepository securityAuditRecordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void disableBootstrap() {
        properties.setEnabled(false);
        properties.setUsername(null);
        properties.setPassword(null);
    }

    @Test
    void firstRunCreatesExactlyOneAdminWithAuditAndRepeatIsNoOp() {
        properties.setEnabled(true);
        properties.setUsername("  bootstrap.admin  ");
        properties.setPassword("test-only-bootstrap-password");

        long auditCountBefore = securityAuditRecordRepository.countByEventTypeAndOutcome(
                "ADMIN_BOOTSTRAP", SecurityAuditOutcome.SUCCESS);

        assertThat(adminBootstrapService.bootstrapIfEnabled()).isEqualTo(AdminBootstrapResult.CREATED);
        entityManager.flush();
        entityManager.clear();

        UserAccount admin = userAccountRepository.findByUsername("bootstrap.admin").orElseThrow();
        assertThat(admin.isActive()).isTrue();
        assertThat(passwordEncoder.matches("test-only-bootstrap-password", admin.getPasswordHash())).isTrue();
        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        assertThat(userAccountRepository.findActiveWithActiveRolesByUsername("bootstrap.admin").orElseThrow()
                .getRoleAssignments())
                .extracting(assignment -> assignment.getRole().getId())
                .containsExactly(adminRole.getId());
        assertThat(securityAuditRecordRepository.countByEventTypeAndOutcome(
                "ADMIN_BOOTSTRAP", SecurityAuditOutcome.SUCCESS)).isEqualTo(auditCountBefore + 1);

        assertThat(adminBootstrapService.bootstrapIfEnabled())
                .isEqualTo(AdminBootstrapResult.SKIPPED_EXISTING_USER);
        assertThat(userAccountRepository.count()).isEqualTo(1);
    }

    @Test
    void anyExistingUserPreventsBootstrap() {
        UserAccount existingUser = UserAccount.create(
                UUID.randomUUID(),
                "existing.user",
                passwordEncoder.encode("test-only-password"),
                "Existing User");
        userAccountRepository.saveAndFlush(existingUser);
        properties.setEnabled(true);
        properties.setUsername("bootstrap.admin");
        properties.setPassword("test-only-bootstrap-password");

        assertThat(adminBootstrapService.bootstrapIfEnabled())
                .isEqualTo(AdminBootstrapResult.SKIPPED_EXISTING_USER);
        assertThat(userAccountRepository.count()).isEqualTo(1);
    }
}
