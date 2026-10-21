package com.minierp.platform.security.useradmin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import com.minierp.platform.persistence.TestDatabaseSafetyInitializer;
import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.persistence.Role;
import com.minierp.platform.security.persistence.RoleCode;
import com.minierp.platform.security.persistence.RoleRepository;
import com.minierp.platform.security.persistence.SecurityAuditOutcome;
import com.minierp.platform.security.persistence.SecurityAuditRecordRepository;
import com.minierp.platform.security.persistence.UserAccount;
import com.minierp.platform.security.persistence.UserAccountRepository;
import com.minierp.platform.security.persistence.UserRole;
import com.minierp.platform.security.persistence.UserRoleRepository;

/** Verifies atomic User Administration persistence against PostgreSQL. / Kiểm tra persistence Quản trị người dùng nguyên tử với PostgreSQL. */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@Transactional
class UserAdministrationServiceIntegrationTest {
    @Autowired private UserAdministrationService service;
    @Autowired private UserAccountRepository users;
    @Autowired private UserRoleRepository userRoles;
    @Autowired private RoleRepository roles;
    @Autowired private SecurityAuditRecordRepository audits;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void adminCreatesUserWithBcryptRoleAndRequiredAudit() {
        Role adminRole = roles.findByCode(RoleCode.ADMIN).orElseThrow();
        UserAccount admin = users.saveAndFlush(UserAccount.create(UUID.randomUUID(), "admin." + UUID.randomUUID(), passwordEncoder.encode("test-only-password"), "Admin"));
        userRoles.saveAndFlush(UserRole.assign(admin, adminRole, null));
        long before = audits.countByEventTypeAndOutcome("USER_CREATED", SecurityAuditOutcome.SUCCESS);

        UserAdministrationResult result = service.create(principal(admin), new UserAdministrationCommand.CreateUser(
                " New.User ", "New User", "test-only-password", Set.of(RoleCode.SALES_STAFF)));

        assertThat(result.username()).isEqualTo("new.user");
        assertThat(result.roles()).containsExactly(RoleCode.SALES_STAFF);
        assertThat(users.findByUsername("new.user").orElseThrow().getPasswordHash()).startsWith("$2");
        assertThat(audits.countByEventTypeAndOutcome("USER_CREATED", SecurityAuditOutcome.SUCCESS)).isEqualTo(before + 1);
    }

    private AuthenticatedPrincipal principal(UserAccount admin) {
        return new AuthenticatedPrincipal(admin.getId(), admin.getUsername(), admin.getDisplayName(), Set.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
