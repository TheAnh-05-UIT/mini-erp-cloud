package com.minierp.platform.security.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Defines the composite identity that prevents duplicate user-to-role assignments. / Xác định khóa kép ngăn gán trùng role cho user.
 */
@Embeddable
public class UserRoleId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    protected UserRoleId() {
    }

    public UserRoleId(UUID userId, UUID roleId) {
        this.userId = Objects.requireNonNull(userId, "userId is required");
        this.roleId = Objects.requireNonNull(roleId, "roleId is required");
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserRoleId that)) {
            return false;
        }
        return userId.equals(that.userId) && roleId.equals(that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleId);
    }
}
