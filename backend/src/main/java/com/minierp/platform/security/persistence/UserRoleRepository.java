package com.minierp.platform.security.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Accesses role assignments without exposing direct table access across modules.
 * Truy cập role assignment mà không cho phép module khác truy cập bảng trực tiếp.
 */
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByIdUserId(UUID userId);

    void deleteByIdUserId(UUID userId);
}
