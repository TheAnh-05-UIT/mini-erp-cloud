package com.minierp.platform.catalog.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import jakarta.persistence.*;

/** Catalog category aggregate. / Aggregate danh muc catalog. */
@Entity
@Table(name = "categories")
public class Category {
    @Id
    private UUID id;
    @Column(nullable = false, length = 50)
    private String code;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Category() {
    }

    public static Category create(UUID id, String code, String name) {
        Category value = new Category();
        value.id = id;
        value.code = normalizeCode(code);
        value.name = normalizeName(name);
        value.active = true;
        return value;
    }

    public void rename(String name) {
        this.name = normalizeName(name);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @PrePersist
    void created() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
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

    public static String normalizeCode(String value) {
        String normalized = require(value, "code").toUpperCase(Locale.ROOT);
        if (normalized.length() > 50)
            throw new IllegalArgumentException("code is too long");
        return normalized;
    }

    public static String normalizeName(String value) {
        String normalized = require(value, "name");
        if (normalized.length() > 150)
            throw new IllegalArgumentException("name is too long");
        return normalized;
    }

    private static String require(String value, String field) {
        if (value == null)
            throw new IllegalArgumentException(field + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty())
            throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
