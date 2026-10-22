package com.minierp.platform.catalog.application;

import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.persistence.RoleCode;

/**
 * Resolves catalog permissions from current refreshed roles. / Kiem tra quyen
 * catalog tu role da lam moi.
 */
@Service
public class CatalogAuthorizationService {
    private static final Map<RoleCode, Set<CatalogPermission>> MATRIX = matrix();

    public void require(AuthenticatedPrincipal actor, CatalogPermission permission) {
        if (!has(actor, permission))
            throw new AccessDeniedException("Catalog permission is required.");
    }

    public boolean has(AuthenticatedPrincipal actor, CatalogPermission permission) {
        if (actor == null || permission == null)
            return false;
        return actor.authorities().stream().map(GrantedAuthority::getAuthority)
                .filter(value -> value.startsWith("ROLE_")).map(value -> role(value.substring(5)))
                .flatMap(Optional::stream).map(MATRIX::get).filter(Objects::nonNull)
                .anyMatch(values -> values.contains(permission));
    }

    private static Optional<RoleCode> role(String value) {
        try {
            return Optional.of(RoleCode.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Map<RoleCode, Set<CatalogPermission>> matrix() {
        EnumMap<RoleCode, Set<CatalogPermission>> map = new EnumMap<>(RoleCode.class);
        Set<CatalogPermission> all = Set.of(CatalogPermission.values());
        map.put(RoleCode.ADMIN, all);
        map.put(RoleCode.PURCHASING_STAFF,
                Set.of(CatalogPermission.CATEGORY_READ, CatalogPermission.CATEGORY_CREATE,
                        CatalogPermission.CATEGORY_UPDATE, CatalogPermission.PRODUCT_READ,
                        CatalogPermission.PRODUCT_CREATE, CatalogPermission.PRODUCT_UPDATE));
        map.put(RoleCode.WAREHOUSE_STAFF, Set.of(CatalogPermission.CATEGORY_READ, CatalogPermission.PRODUCT_READ));
        map.put(RoleCode.SALES_STAFF, Set.of(CatalogPermission.CATEGORY_READ, CatalogPermission.PRODUCT_READ));
        map.put(RoleCode.MANAGER, Set.of(CatalogPermission.CATEGORY_READ, CatalogPermission.PRODUCT_READ));
        return Map.copyOf(map);
    }
}
