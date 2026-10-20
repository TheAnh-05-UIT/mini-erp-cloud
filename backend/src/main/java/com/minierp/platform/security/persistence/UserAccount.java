package com.minierp.platform.security.persistence;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Represents an application identity with a normalized username and BCrypt hash only.
 * Đại diện danh tính ứng dụng với username đã chuẩn hóa và chỉ lưu BCrypt hash.
 */
@Entity
@Table(name = "users")
public class UserAccount {

    private static final Pattern BCRYPT_HASH = Pattern.compile("^\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}$");

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String username;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "user")
    private Set<UserRole> roleAssignments = new LinkedHashSet<>();

    protected UserAccount() {
    }

    private UserAccount(UUID id, String username, String passwordHash, String displayName, boolean active) {
        this.id = id;
        this.username = normalizeUsername(username);
        this.passwordHash = requireBcryptHash(passwordHash);
        this.displayName = requireNonBlank(displayName, "displayName");
        this.active = active;
    }

    public static UserAccount create(UUID id, String username, String passwordHash, String displayName) {
        return new UserAccount(requireId(id), username, passwordHash, displayName, true);
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<UserRole> getRoleAssignments() {
        return Set.copyOf(roleAssignments);
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public static String normalizeUsername(String value) {
        return requireNonBlank(value, "username").trim().toLowerCase(Locale.ROOT);
    }

    private static UUID requireId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("id is required");
        }
        return value;
    }

    private static String requireBcryptHash(String value) {
        if (value == null || !BCRYPT_HASH.matcher(value).matches()) {
            throw new IllegalArgumentException("passwordHash must be a BCrypt hash with work factor 12");
        }
        return value;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
