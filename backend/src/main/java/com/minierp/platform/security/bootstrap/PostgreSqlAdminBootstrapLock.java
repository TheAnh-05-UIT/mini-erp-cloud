package com.minierp.platform.security.bootstrap;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;

/**
 * Serializes concurrent first-user bootstrap attempts with a transaction-scoped PostgreSQL lock.
 * Tuần tự hóa các lần bootstrap user đầu tiên đồng thời bằng PostgreSQL lock theo transaction.
 * The lock is released automatically when the transaction ends. / Lock được tự giải phóng khi transaction kết thúc.
 */
@Repository
class PostgreSqlAdminBootstrapLock {

    private static final long ADMIN_BOOTSTRAP_LOCK_KEY = 4_248_165_321L;

    private final EntityManager entityManager;

    PostgreSqlAdminBootstrapLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    void acquire() {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(" + ADMIN_BOOTSTRAP_LOCK_KEY + ")")
                .getSingleResult();
    }
}
