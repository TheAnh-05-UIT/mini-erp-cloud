package com.minierp.platform.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.minierp.platform.catalog.domain.Category;
import com.minierp.platform.catalog.domain.Product;
import com.minierp.platform.catalog.domain.UnitOfMeasure;

/** Verifies catalog canonicalization and immutable SKU behavior. / Kiem tra chuan hoa catalog va SKU bat bien. */
class CatalogDomainTest {
    @Test void normalizesCategoryCodeAndProductSku() {
        Category category = Category.create(UUID.randomUUID(), "  office  ", " Office supplies ");
        Product product = Product.create(UUID.randomUUID(), " sku-001 ", "Paper", null, category, UnitOfMeasure.PIECE);
        assertThat(category.getCode()).isEqualTo("OFFICE");
        assertThat(product.getSku()).isEqualTo("SKU-001");
    }

    @Test void acceptsOnlyApprovedUnitsAtTypeBoundary() {
        assertThat(UnitOfMeasure.values()).containsExactly(UnitOfMeasure.PIECE, UnitOfMeasure.SET, UnitOfMeasure.BOX);
    }

    @Test void rejectsBlankOrOversizedSku() {
        assertThatThrownBy(() -> Product.normalizeSku(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Product.normalizeSku("x".repeat(101))).isInstanceOf(IllegalArgumentException.class);
    }
}
