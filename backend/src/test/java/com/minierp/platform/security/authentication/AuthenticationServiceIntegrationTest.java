package com.minierp.platform.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import com.minierp.platform.security.persistence.UserRole;
import com.minierp.platform.security.persistence.UserRoleRepository;

/**
 * Verifies credential checks and security audit behavior against PostgreSQL. / Kiểm tra xác thực credentials và security audit với PostgreSQL.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@Transactional
class AuthenticationServiceIntegrationTest {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private SecurityAuthenticationAuditRecorder securityAuthenticationAuditRecorder;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SecurityAuditRecordRepository securityAuditRecordRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void authenticatesAgainstPostgreSqlAndPersistsSuccessfulLoginAudit() {
        String username = "auth." + UUID.randomUUID().toString().substring(0, 12);
        UserAccount user = userAccountRepository.saveAndFlush(UserAccount.create(
                UUID.randomUUID(),
                username,
                passwordEncoder.encode("test-only-password"),
                "Authentication Service Test User"));
        Role role = roleRepository.findByCode(RoleCode.SALES_STAFF).orElseThrow();
        userRoleRepository.saveAndFlush(UserRole.assign(user, role, null));
        entityManager.clear();

        long auditCountBefore = securityAuditRecordRepository.countByEventTypeAndOutcome(
                "LOGIN", SecurityAuditOutcome.SUCCESS);

        AuthenticatedPrincipal principal = authenticationService.authenticate(
                "  " + username.toUpperCase() + "  ",
                "test-only-password");

        assertThat(principal.userId()).isEqualTo(user.getId());
        assertThat(principal.authorities()).containsExactly(new SimpleGrantedAuthority("ROLE_SALES_STAFF"));
        assertThat(securityAuditRecordRepository.countByEventTypeAndOutcome("LOGIN", SecurityAuditOutcome.SUCCESS))
                .isEqualTo(auditCountBefore + 1);
    }

    @Test
    void persistsLogoutAuditWithNoSessionMetadata() {
        UUID userId = UUID.randomUUID();
        long auditCountBefore = securityAuditRecordRepository.countByEventTypeAndOutcome(
                "LOGOUT", SecurityAuditOutcome.SUCCESS);

        securityAuthenticationAuditRecorder.recordLogout(userId);
        entityManager.flush();

        assertThat(securityAuditRecordRepository.countByEventTypeAndOutcome("LOGOUT", SecurityAuditOutcome.SUCCESS))
                .isEqualTo(auditCountBefore + 1);
        Object metadata = entityManager.createNativeQuery("""
                select metadata
                from security_audit_records
                where event_type = 'LOGOUT'
                  and target_reference = :targetReference
                """)
                .setParameter("targetReference", userId.toString())
                .getSingleResult();
        assertThat(metadata).isNull();
    }
}
