package com.minierp.platform.security.useradmin;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;

/** Serializes mutations that can affect the last active Admin. / Tuần tự hóa mutation có thể ảnh hưởng Admin active cuối cùng. */
@Repository
class PostgreSqlUserAdministrationLock {

    private static final long USER_ADMINISTRATION_LOCK_KEY = 5_104_403_219L;

    private final EntityManager entityManager;

    PostgreSqlUserAdministrationLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    void acquire() {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(" + USER_ADMINISTRATION_LOCK_KEY + ")")
                .getSingleResult();
    }
}
