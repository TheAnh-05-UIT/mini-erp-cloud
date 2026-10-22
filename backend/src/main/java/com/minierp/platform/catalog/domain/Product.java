package com.minierp.platform.catalog.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import jakarta.persistence.*;

/**
 * Catalog product aggregate; SKU is intentionally immutable. / Aggregate san
 * pham; SKU co dinh sau khi tao.
 */
@Entity
@Table(name = "products")
public class Product {
    @Id
    private UUID id;
    @Column(nullable = false, length = 100, updatable = false)
    private String sku;
    @Column(nullable = false, length = 250)
    private String name;
    @Column(length = 2000)
    private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 20)
    private UnitOfMeasure unitOfMeasure;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public static Product create(UUID id, String sku, String name, String description, Category category,
            UnitOfMeasure unit) {
        Product value = new Product();
        value.id = id;
        value.sku = normalizeSku(sku);
        value.update(name, description, category, unit);
        value.active = true;
        return value;
    }

    public void update(String name, String description, Category category, UnitOfMeasure unit) {
        this.name = normalizeName(name);
        this.description = normalizeDescription(description);
        if (category == null)
            throw new IllegalArgumentException("category is required");
        if (unit == null)
            throw new IllegalArgumentException("unitOfMeasure is required");
        this.category = category;
        this.unitOfMeasure = unit;
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

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public UnitOfMeasure getUnitOfMeasure() {
        return unitOfMeasure;
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

    public static String normalizeSku(String value) {
        String normalized = require(value, "sku").toUpperCase(Locale.ROOT);
        if (normalized.length() > 100)
            throw new IllegalArgumentException("sku is too long");
        return normalized;
    }

    private static String normalizeName(String value) {
        String normalized = require(value, "name");
        if (normalized.length() > 250)
            throw new IllegalArgumentException("name is too long");
        return normalized;
    }

    private static String normalizeDescription(String value) {
        if (value == null || value.isBlank())
            return null;
        String normalized = value.trim();
        if (normalized.length() > 2000)
            throw new IllegalArgumentException("description is too long");
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
