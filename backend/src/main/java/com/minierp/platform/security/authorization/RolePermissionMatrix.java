package com.minierp.platform.security.authorization;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.minierp.platform.security.persistence.RoleCode;

/** Holds the immutable, application-defined User Administration role matrix. / Giữ ma trận role bất biến do ứng dụng định nghĩa cho Quản trị người dùng. */
@Component
public final class RolePermissionMatrix {

    private static final Set<UserAdministrationPermission> ADMIN_PERMISSIONS =
            Set.copyOf(EnumSet.allOf(UserAdministrationPermission.class));

    private static final Map<RoleCode, Set<UserAdministrationPermission>> USER_ADMINISTRATION_PERMISSIONS =
            userAdministrationPermissions();

    public Set<UserAdministrationPermission> permissionsFor(RoleCode roleCode) {
        if (roleCode == null) {
            return Set.of();
        }
        return USER_ADMINISTRATION_PERMISSIONS.getOrDefault(roleCode, Set.of());
    }

    private static Map<RoleCode, Set<UserAdministrationPermission>> userAdministrationPermissions() {
        EnumMap<RoleCode, Set<UserAdministrationPermission>> permissions = new EnumMap<>(RoleCode.class);
        permissions.put(RoleCode.ADMIN, ADMIN_PERMISSIONS);
        permissions.put(RoleCode.PURCHASING_STAFF, Set.of());
        permissions.put(RoleCode.WAREHOUSE_STAFF, Set.of());
        permissions.put(RoleCode.SALES_STAFF, Set.of());
        // Manager user-read access is pending explicit approval, so default deny applies.
        // Quyền đọc user của Manager đang chờ phê duyệt rõ ràng, nên mặc định từ chối.
        permissions.put(RoleCode.MANAGER, Set.of());
        return Map.copyOf(permissions);
    }
}
