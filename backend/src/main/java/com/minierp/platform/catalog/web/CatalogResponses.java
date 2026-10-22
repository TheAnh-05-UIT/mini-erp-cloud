package com.minierp.platform.catalog.web;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import com.minierp.platform.catalog.application.CatalogResult;
import com.minierp.platform.catalog.domain.UnitOfMeasure;

/** Safe HTTP catalog response DTOs. / DTO response catalog an toan. */
public final class CatalogResponses {
    private CatalogResponses() {
    }

    public record Category(UUID id, String code, String name, boolean active, Instant createdAt, Instant updatedAt) {
        static Category from(CatalogResult.Category value) {
            return new Category(value.id(), value.code(), value.name(), value.active(), value.createdAt(),
                    value.updatedAt());
        }
    }

    public record Product(UUID id, String sku, String name, String description, UUID categoryId, String categoryCode,
            String categoryName, UnitOfMeasure unitOfMeasure, boolean active, Instant createdAt, Instant updatedAt) {
        static Product from(CatalogResult.Product value) {
            return new Product(value.id(), value.sku(), value.name(), value.description(), value.categoryId(),
                    value.categoryCode(), value.categoryName(), value.unitOfMeasure(), value.active(),
                    value.createdAt(), value.updatedAt());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        static <S, T> PageResponse<T> from(Page<S> page, java.util.function.Function<S, T> map) {
            return new PageResponse<>(page.map(map).getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
