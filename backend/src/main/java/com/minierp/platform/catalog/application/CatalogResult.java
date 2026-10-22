package com.minierp.platform.catalog.application;

import java.time.Instant;
import java.util.UUID;
import com.minierp.platform.catalog.domain.UnitOfMeasure;

/** Safe catalog projections. / Projection catalog an toan. */
public final class CatalogResult {
    private CatalogResult() {
    }

    public record Category(UUID id, String code, String name, boolean active, Instant createdAt, Instant updatedAt) {
    }

    public record Product(UUID id, String sku, String name, String description, UUID categoryId, String categoryCode,
            String categoryName, UnitOfMeasure unitOfMeasure, boolean active, Instant createdAt, Instant updatedAt) {
    }
}
