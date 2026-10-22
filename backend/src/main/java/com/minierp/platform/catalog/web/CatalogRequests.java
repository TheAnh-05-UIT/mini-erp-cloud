package com.minierp.platform.catalog.web;

import java.util.UUID;
import com.minierp.platform.catalog.domain.UnitOfMeasure;
import jakarta.validation.constraints.*;

/** Boundary DTOs for catalog mutations. / DTO bien cho mutation catalog. */
public final class CatalogRequests {
    private CatalogRequests() {
    }

    public record CreateCategory(@NotBlank @Size(max = 50) String code, @NotBlank @Size(max = 150) String name) {
    }

    public record UpdateCategory(@NotBlank @Size(max = 150) String name) {
    }

    public record CreateProduct(@NotBlank @Size(max = 100) String sku, @NotBlank @Size(max = 250) String name,
            @Size(max = 2000) String description, @NotNull UUID categoryId, @NotNull UnitOfMeasure unitOfMeasure) {
    }

    public record UpdateProduct(@NotBlank @Size(max = 250) String name, @Size(max = 2000) String description,
            @NotNull UUID categoryId, @NotNull UnitOfMeasure unitOfMeasure) {
    }
}
