package com.minierp.platform.security.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Loads role definitions needed for authorization and bootstrap assignment. / Tải định nghĩa role cho authorization và gán role khi bootstrap.
 */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(RoleCode code);
}
