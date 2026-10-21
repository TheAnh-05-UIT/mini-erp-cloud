package com.minierp.platform.security.useradmin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;

import com.minierp.platform.persistence.TestDatabaseSafetyInitializer;
import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.persistence.Role;
import com.minierp.platform.security.persistence.RoleCode;
import com.minierp.platform.security.persistence.RoleRepository;
import com.minierp.platform.security.persistence.UserAccount;
import com.minierp.platform.security.persistence.UserAccountRepository;
import com.minierp.platform.security.persistence.UserRole;
import com.minierp.platform.security.persistence.UserRoleRepository;

/** Proves PostgreSQL rolls back mutations when required audit fails. / Chứng minh PostgreSQL rollback mutation khi audit bắt buộc thất bại. */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class, classes = UserAdministrationAuditRollbackIntegrationTest.FailingAuditConfiguration.class)
class UserAdministrationAuditRollbackIntegrationTest {
    @Autowired UserAdministrationService service;
    @Autowired UserAccountRepository users;
    @Autowired UserRoleRepository userRoles;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactionTemplate;
    private final Set<UUID> ownedUserIds = new LinkedHashSet<>();

    @AfterEach void cleanupOwnedFixtures() {
        transactionTemplate.executeWithoutResult(status -> {
            for (UUID id : ownedUserIds) {
                entityManager.createNativeQuery("delete from security_audit_records where actor_user_id = :id or target_user_id = :id").setParameter("id", id).executeUpdate();
                entityManager.createNativeQuery("delete from user_roles where user_id = :id or assigned_by_user_id = :id").setParameter("id", id).executeUpdate();
            }
            for (UUID id : ownedUserIds) entityManager.createNativeQuery("delete from users where id = :id").setParameter("id", id).executeUpdate();
        });
        for (UUID id : ownedUserIds) if (users.existsById(id)) throw new AssertionError("Owned fixture cleanup failed: " + id);
    }

    @Test void createRollsBackWhenAuditFails() {
        UserAccount admin = admin(); String username = "rollback.create." + UUID.randomUUID();
        assertThatThrownBy(() -> service.create(principal(admin), new UserAdministrationCommand.CreateUser(username, "Created", "test-only-password", Set.of(RoleCode.SALES_STAFF)))).isInstanceOf(AuditFailure.class);
        org.assertj.core.api.Assertions.assertThat(users.findByUsername(username)).isEmpty();
    }
    @Test void updateRollsBackWhenAuditFails() {
        UserAccount admin=admin(), target=user("Before", RoleCode.SALES_STAFF);
        assertThatThrownBy(() -> service.updateDisplayName(principal(admin), target.getId(), new UserAdministrationCommand.UpdateDisplayName("After"))).isInstanceOf(AuditFailure.class);
        org.assertj.core.api.Assertions.assertThat(users.findById(target.getId()).orElseThrow().getDisplayName()).isEqualTo("Before");
    }
    @Test void activateRollsBackWhenAuditFails() {
        UserAccount admin=admin(), target=user("Inactive", RoleCode.SALES_STAFF); target.setActive(false); users.saveAndFlush(target);
        assertThatThrownBy(() -> service.activate(principal(admin), target.getId())).isInstanceOf(AuditFailure.class);
        org.assertj.core.api.Assertions.assertThat(users.findById(target.getId()).orElseThrow().isActive()).isFalse();
    }
    @Test void deactivateRollsBackWhenAuditFails() {
        UserAccount admin=admin(), target=user("Active", RoleCode.SALES_STAFF);
        assertThatThrownBy(() -> service.deactivate(principal(admin), target.getId())).isInstanceOf(AuditFailure.class);
        org.assertj.core.api.Assertions.assertThat(users.findById(target.getId()).orElseThrow().isActive()).isTrue();
    }
    @Test void replaceRolesRollsBackWhenAuditFails() {
        UserAccount admin=admin(), target=user("Roles", RoleCode.SALES_STAFF);
        assertThatThrownBy(() -> service.replaceRoles(principal(admin), target.getId(), new UserAdministrationCommand.ReplaceRoles(Set.of(RoleCode.WAREHOUSE_STAFF)))).isInstanceOf(AuditFailure.class);
        UUID salesRoleId = roles.findByCode(RoleCode.SALES_STAFF).orElseThrow().getId();
        org.assertj.core.api.Assertions.assertThat(userRoles.findByIdUserId(target.getId()))
                .extracting(assignment -> assignment.getId().getRoleId())
                .containsExactly(salesRoleId);
    }
    private UserAccount admin(){ return user("Admin",RoleCode.ADMIN); }
    private UserAccount user(String name, RoleCode code){ Role role=roles.findByCode(code).orElseThrow(); UUID id=UUID.randomUUID(); ownedUserIds.add(id); UserAccount u=users.saveAndFlush(UserAccount.create(id,"rollback."+UUID.randomUUID(),encoder.encode("test-only-password"),name)); userRoles.saveAndFlush(UserRole.assign(u,role,null)); return u; }
    private AuthenticatedPrincipal principal(UserAccount u){return new AuthenticatedPrincipal(u.getId(),u.getUsername(),u.getDisplayName(),Set.of(new SimpleGrantedAuthority("ROLE_ADMIN")));}
    static class AuditFailure extends RuntimeException {}
    @TestConfiguration static class FailingAuditConfiguration { @Bean @Primary UserAdministrationAuditRecorder failingAudit(){ return (actor,target,event,metadata) -> { throw new AuditFailure(); }; } }
}
