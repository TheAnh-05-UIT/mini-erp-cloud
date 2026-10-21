package com.minierp.platform.security.useradmin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import com.minierp.platform.persistence.ConcurrencyTestDatabaseSafetyInitializer;
import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.persistence.Role;
import com.minierp.platform.security.persistence.RoleCode;
import com.minierp.platform.security.persistence.RoleRepository;
import com.minierp.platform.security.persistence.UserAccount;
import com.minierp.platform.security.persistence.UserAccountRepository;
import com.minierp.platform.security.persistence.UserRole;
import com.minierp.platform.security.persistence.UserRoleRepository;

/**
 * Verifies that PostgreSQL transaction advisory locking preserves the last active
 * Admin invariant. / Kiem tra advisory lock cua PostgreSQL bao ve bat bien
 * Admin active cuoi cung.
 */
@SpringBootTest
@ActiveProfiles("concurrency-test")
@ContextConfiguration(initializers = ConcurrencyTestDatabaseSafetyInitializer.class)
class UserAdministrationLastAdminConcurrencyIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private UserAdministrationService service;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final Set<UUID> ownedUserIds = new LinkedHashSet<>();
    private final Set<UUID> ownedAuditIds = new LinkedHashSet<>();

    @AfterEach
    void cleanupOwnedFixtures() {
        captureOwnedAuditIds();
        transactionTemplate.executeWithoutResult(status -> {
            for (UUID auditId : ownedAuditIds) {
                entityManager.createNativeQuery("delete from security_audit_records where id = :id")
                        .setParameter("id", auditId)
                        .executeUpdate();
            }
            for (UUID userId : ownedUserIds) {
                entityManager.createNativeQuery("delete from user_roles where user_id = :id or assigned_by_user_id = :id")
                        .setParameter("id", userId)
                        .executeUpdate();
            }
            for (UUID userId : ownedUserIds) {
                entityManager.createNativeQuery("delete from users where id = :id")
                        .setParameter("id", userId)
                        .executeUpdate();
            }
        });
        entityManager.clear();
        for (UUID userId : ownedUserIds) {
            assertThat(users.existsById(userId)).as("owned user cleanup for %s", userId).isFalse();
        }
        for (UUID auditId : ownedAuditIds) {
            Number count = (Number) entityManager.createNativeQuery("select count(*) from security_audit_records where id = :id")
                    .setParameter("id", auditId)
                    .getSingleResult();
            assertThat(count.longValue()).as("owned audit cleanup for %s", auditId).isZero();
        }
    }

    @Test
    void concurrentDeactivationLeavesOneActiveAdmin() throws Exception {
        AdminPair admins = isolatedAdminPair();

        List<AttemptResult> results = runConcurrently(
                () -> service.deactivate(principal(admins.second()), admins.first().getId()),
                () -> service.deactivate(principal(admins.first()), admins.second().getId()));

        assertExactlyOneCommittedMutation(results);
        assertCommittedState(admins, "USER_DEACTIVATED");
    }

    @Test
    void concurrentAdminRoleRemovalLeavesOneActiveAdmin() throws Exception {
        AdminPair admins = isolatedAdminPair();

        List<AttemptResult> results = runConcurrently(
                () -> service.replaceRoles(principal(admins.second()), admins.first().getId(),
                        new UserAdministrationCommand.ReplaceRoles(Set.of(RoleCode.SALES_STAFF))),
                () -> service.replaceRoles(principal(admins.first()), admins.second().getId(),
                        new UserAdministrationCommand.ReplaceRoles(Set.of(RoleCode.WAREHOUSE_STAFF))));

        assertExactlyOneCommittedMutation(results);
        assertCommittedState(admins, "USER_ROLES_REPLACED");
    }

    @Test
    void concurrentDeactivationAndAdminRoleRemovalLeaveOneActiveAdmin() throws Exception {
        AdminPair admins = isolatedAdminPair();

        List<AttemptResult> results = runConcurrently(
                () -> service.deactivate(principal(admins.second()), admins.first().getId()),
                () -> service.replaceRoles(principal(admins.first()), admins.second().getId(),
                        new UserAdministrationCommand.ReplaceRoles(Set.of(RoleCode.SALES_STAFF))));

        assertExactlyOneCommittedMutation(results);
        assertCommittedState(admins, null);
    }

    private AdminPair isolatedAdminPair() {
        assertThat(users.countActiveAdmins())
                .as("the dedicated concurrency database must not contain unrelated active Admin users")
                .isZero();
        Role adminRole = roles.findByCode(RoleCode.ADMIN).orElseThrow();
        UserAccount first = createAdmin("concurrency.admin.a.", adminRole);
        UserAccount second = createAdmin("concurrency.admin.b.", adminRole);
        entityManager.clear();
        assertThat(users.countActiveAdmins()).isEqualTo(2);
        return new AdminPair(first, second);
    }

    private UserAccount createAdmin(String usernamePrefix, Role adminRole) {
        UUID id = UUID.randomUUID();
        ownedUserIds.add(id);
        UserAccount account = users.saveAndFlush(UserAccount.create(
                id,
                usernamePrefix + UUID.randomUUID(),
                passwordEncoder.encode("test-only-password"),
                "Concurrency Admin"));
        userRoles.saveAndFlush(UserRole.assign(account, adminRole, null));
        return account;
    }

    private List<AttemptResult> runConcurrently(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AttemptResult> firstResult = executor.submit(attempt(first, ready, start));
            Future<AttemptResult> secondResult = executor.submit(attempt(second, ready, start));
            assertThat(ready.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            start.countDown();
            return List.of(firstResult.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                    secondResult.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        }
    }

    private Callable<AttemptResult> attempt(Callable<?> operation, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Concurrent test start timed out");
            }
            try {
                operation.call();
                return AttemptResult.success();
            } catch (Throwable failure) {
                return AttemptResult.failure(failure);
            }
        };
    }

    private void assertExactlyOneCommittedMutation(List<AttemptResult> results) {
        assertThat(results).extracting(AttemptResult::succeeded).containsExactlyInAnyOrder(true, false);
        assertThat(results).filteredOn(result -> !result.succeeded()).allSatisfy(result ->
                assertThat(result.failure()).isInstanceOf(LastActiveAdminConflictException.class));
    }

    private void assertCommittedState(AdminPair admins, String exactEventType) {
        entityManager.clear();
        assertThat(users.countActiveAdmins()).isEqualTo(1);
        long auditCount = countOwnedSuccessfulAudits(admins);
        assertThat(auditCount).isEqualTo(1);
        if (exactEventType != null) {
            Number exactEventCount = (Number) entityManager.createNativeQuery("""
                    select count(*) from security_audit_records
                    where event_type = :eventType
                      and (actor_user_id = :firstId or actor_user_id = :secondId
                           or target_user_id = :firstId or target_user_id = :secondId)
                    """)
                    .setParameter("eventType", exactEventType)
                    .setParameter("firstId", admins.first().getId())
                    .setParameter("secondId", admins.second().getId())
                    .getSingleResult();
            assertThat(exactEventCount.longValue()).isEqualTo(1);
        }
    }

    private long countOwnedSuccessfulAudits(AdminPair admins) {
        Number count = (Number) entityManager.createNativeQuery("""
                select count(*) from security_audit_records
                where outcome = 'SUCCESS'
                  and (actor_user_id = :firstId or actor_user_id = :secondId
                       or target_user_id = :firstId or target_user_id = :secondId)
                """)
                .setParameter("firstId", admins.first().getId())
                .setParameter("secondId", admins.second().getId())
                .getSingleResult();
        return count.longValue();
    }

    private void captureOwnedAuditIds() {
        if (ownedUserIds.isEmpty()) {
            return;
        }
        List<UUID> collected = new ArrayList<>();
        for (UUID userId : ownedUserIds) {
            entityManager.createNativeQuery("select id from security_audit_records where actor_user_id = :id or target_user_id = :id")
                    .setParameter("id", userId)
                    .getResultList()
                    .forEach(value -> collected.add((UUID) value));
        }
        ownedAuditIds.addAll(collected);
    }

    private AuthenticatedPrincipal principal(UserAccount account) {
        return new AuthenticatedPrincipal(account.getId(), account.getUsername(), account.getDisplayName(),
                Set.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private record AdminPair(UserAccount first, UserAccount second) {
    }

    private record AttemptResult(boolean succeeded, Throwable failure) {
        static AttemptResult success() {
            return new AttemptResult(true, null);
        }

        static AttemptResult failure(Throwable failure) {
            return new AttemptResult(false, failure);
        }
    }
}
