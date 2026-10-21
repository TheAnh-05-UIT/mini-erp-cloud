package com.minierp.platform.security.authorization;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.persistence.RoleCode;

/** Performs capability checks only; data scope, state, and SoD remain resource-layer checks. / Chỉ kiểm tra năng lực; data scope, trạng thái và SoD vẫn được kiểm tra tại tầng resource. */
@Service
public class UserAdministrationAuthorizationService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final RolePermissionMatrix rolePermissionMatrix;

    public UserAdministrationAuthorizationService(RolePermissionMatrix rolePermissionMatrix) {
        this.rolePermissionMatrix = rolePermissionMatrix;
    }

    public boolean hasPermission(AuthenticatedPrincipal principal, UserAdministrationPermission permission) {
        if (principal == null || permission == null) {
            return false;
        }

        return permissionsFor(principal).contains(permission);
    }

    public void requirePermission(AuthenticatedPrincipal principal, UserAdministrationPermission permission) {
        if (!hasPermission(principal, permission)) {
            throw new AccessDeniedException("User Administration permission is required.");
        }
    }

    private Set<UserAdministrationPermission> permissionsFor(AuthenticatedPrincipal principal) {
        Set<UserAdministrationPermission> permissions = EnumSet.noneOf(UserAdministrationPermission.class);
        principal.authorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(this::toRoleCode)
                .flatMap(java.util.Optional::stream)
                .map(rolePermissionMatrix::permissionsFor)
                .forEach(permissions::addAll);
        return permissions;
    }

    private java.util.Optional<RoleCode> toRoleCode(String authority) {
        if (authority == null || !authority.startsWith(ROLE_PREFIX)) {
            return java.util.Optional.empty();
        }

        try {
            return java.util.Optional.of(RoleCode.valueOf(authority.substring(ROLE_PREFIX.length())));
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }
}
