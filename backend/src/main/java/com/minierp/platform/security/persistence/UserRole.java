package com.minierp.platform.security.persistence;

import java.time.Instant;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Maps an assigned role to a user and preserves who assigned it when known. / Ánh xạ role đã gán cho user và lưu người gán khi có thông tin.
 */
@Entity
@Table(name = "user_roles")
public class UserRole {

    @EmbeddedId
    private UserRoleId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id")
    private UserAccount assignedByUser;

    @jakarta.persistence.Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    protected UserRole() {
    }

    private UserRole(UserAccount user, Role role, UserAccount assignedByUser) {
        this.id = new UserRoleId(user.getId(), role.getId());
        this.user = user;
        this.role = role;
        this.assignedByUser = assignedByUser;
    }

    public static UserRole assign(UserAccount user, Role role, UserAccount assignedByUser) {
        if (user == null || role == null) {
            throw new IllegalArgumentException("user and role are required");
        }
        return new UserRole(user, role, assignedByUser);
    }

    public UserRoleId getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    @PrePersist
    void initializeAssignedAt() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
    }
}
