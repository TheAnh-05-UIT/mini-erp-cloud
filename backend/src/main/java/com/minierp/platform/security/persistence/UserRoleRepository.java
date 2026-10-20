package com.minierp.platform.security.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accesses role assignments without exposing direct table access across modules.
 * Truy cập role assignment mà không cho phép module khác truy cập bảng trực tiếp.
 */
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
}
