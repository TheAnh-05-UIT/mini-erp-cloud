package com.minierp.platform.catalog.application;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.minierp.platform.catalog.domain.*;
import com.minierp.platform.catalog.infrastructure.*;
import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.persistence.UserAccountRepository;

/**
 * Transactional Catalog use cases and required audit. / Use case Catalog giao
 * dich kem audit bat buoc.
 */
@Service
public class CatalogService {
    private final CatalogAuthorizationService authorization;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductTransactionHistoryChecker history;
    private final BusinessAuditRecordRepository audits;
    private final UserAccountRepository users;

    public CatalogService(CatalogAuthorizationService authorization, CategoryRepository categories,
            ProductRepository products, ProductTransactionHistoryChecker history, BusinessAuditRecordRepository audits,
            UserAccountRepository users) {
        this.authorization = authorization;
        this.categories = categories;
        this.products = products;
        this.history = history;
        this.audits = audits;
        this.users = users;
    }

    @Transactional
    public CatalogResult.Category createCategory(AuthenticatedPrincipal actor, String code, String name) {
        authorization.require(actor, CatalogPermission.CATEGORY_CREATE);
        String normalized = Category.normalizeCode(code);
        if (categories.existsByCode(normalized))
            throw new CatalogConflictException("DUPLICATE_CATEGORY_CODE");
        if (categories.existsByNormalizedName(Category.normalizeName(name)))
            throw new CatalogConflictException("DUPLICATE_CATEGORY_NAME");
        Category category = categories.save(Category.create(UUID.randomUUID(), normalized, name));
        audit(actor, "CATEGORY_CREATED", "CATEGORY", category.getId(), category.getCode(), null, "ACTIVE");
        return category(category);
    }

    @Transactional(readOnly = true)
    public Page<CatalogResult.Category> listCategories(AuthenticatedPrincipal actor, String q, Boolean active,
            Pageable page) {
        authorization.require(actor, CatalogPermission.CATEGORY_READ);
        return categories.search(normalizeQuery(q), active, page).map(this::category);
    }

    @Transactional(readOnly = true)
    public CatalogResult.Category category(AuthenticatedPrincipal actor, UUID id) {
        authorization.require(actor, CatalogPermission.CATEGORY_READ);
        return category(categoryEntity(id));
    }

    @Transactional
    public CatalogResult.Category updateCategory(AuthenticatedPrincipal actor, UUID id, String name) {
        authorization.require(actor, CatalogPermission.CATEGORY_UPDATE);
        Category value = categoryEntity(id);
        String normalized = Category.normalizeName(name);
        if (!value.getName().equalsIgnoreCase(normalized) && categories.existsByNormalizedName(normalized))
            throw new CatalogConflictException("DUPLICATE_CATEGORY_NAME");
        value.rename(normalized);
        audit(actor, "CATEGORY_UPDATED", "CATEGORY", id, value.getCode(), null, null);
        return category(value);
    }

    @Transactional
    public CatalogResult.Category setCategoryActive(AuthenticatedPrincipal actor, UUID id, boolean active) {
        authorization.require(actor,
                active ? CatalogPermission.CATEGORY_ACTIVATE : CatalogPermission.CATEGORY_DEACTIVATE);
        Category value = categoryEntity(id);
        if (!active && products.existsByCategoryIdAndActiveTrue(id))
            throw new CatalogConflictException("CATEGORY_HAS_ACTIVE_PRODUCTS");
        String before = value.isActive() ? "ACTIVE" : "INACTIVE";
        value.setActive(active);
        audit(actor, active ? "CATEGORY_ACTIVATED" : "CATEGORY_DEACTIVATED", "CATEGORY", id, value.getCode(), before,
                active ? "ACTIVE" : "INACTIVE");
        return category(value);
    }

    @Transactional
    public CatalogResult.Product createProduct(AuthenticatedPrincipal actor, String sku, String name,
            String description, UUID categoryId, UnitOfMeasure unit) {
        authorization.require(actor, CatalogPermission.PRODUCT_CREATE);
        String normalized = Product.normalizeSku(sku);
        if (products.existsBySku(normalized))
            throw new CatalogConflictException("DUPLICATE_SKU");
        Category category = activeCategory(categoryId);
        Product product = products
                .save(Product.create(UUID.randomUUID(), normalized, name, description, category, unit));
        audit(actor, "PRODUCT_CREATED", "PRODUCT", product.getId(), product.getSku(), null, "ACTIVE");
        return product(product);
    }

    @Transactional(readOnly = true)
    public Page<CatalogResult.Product> listProducts(AuthenticatedPrincipal actor, String q, UUID categoryId,
            Boolean active, Pageable page) {
        authorization.require(actor, CatalogPermission.PRODUCT_READ);
        return products.search(normalizeQuery(q), categoryId, active, page).map(this::product);
    }

    @Transactional(readOnly = true)
    public CatalogResult.Product product(AuthenticatedPrincipal actor, UUID id) {
        authorization.require(actor, CatalogPermission.PRODUCT_READ);
        return product(productEntity(id));
    }

    @Transactional
    public CatalogResult.Product updateProduct(AuthenticatedPrincipal actor, UUID id, String name, String description,
            UUID categoryId, UnitOfMeasure unit) {
        authorization.require(actor, CatalogPermission.PRODUCT_UPDATE);
        Product value = productEntity(id);
        Category category = activeCategory(categoryId);
        if (!value.getCategory().getId().equals(categoryId)
                && history.historyState(id) != ProductTransactionHistoryChecker.HistoryState.NO_HISTORY)
            throw new CatalogConflictException("PRODUCT_CATEGORY_REASSIGNMENT_NOT_ALLOWED");
        value.update(name, description, category, unit);
        audit(actor, "PRODUCT_UPDATED", "PRODUCT", id, value.getSku(), null, null);
        return product(value);
    }

    @Transactional
    public CatalogResult.Product setProductActive(AuthenticatedPrincipal actor, UUID id, boolean active) {
        authorization.require(actor,
                active ? CatalogPermission.PRODUCT_ACTIVATE : CatalogPermission.PRODUCT_DEACTIVATE);
        Product value = productEntity(id);
        String before = value.isActive() ? "ACTIVE" : "INACTIVE";
        value.setActive(active);
        audit(actor, active ? "PRODUCT_ACTIVATED" : "PRODUCT_DEACTIVATED", "PRODUCT", id, value.getSku(), before,
                active ? "ACTIVE" : "INACTIVE");
        return product(value);
    }

    private void audit(AuthenticatedPrincipal actor, String action, String type, UUID id, String ref, String from,
            String to) {
        audits.save(BusinessAuditRecord.create(UUID.randomUUID(), users.getReferenceById(actor.userId()), action, type,
                id, ref, from, to));
    }

    private Category categoryEntity(UUID id) {
        return categories.findById(id).orElseThrow(CatalogNotFoundException::new);
    }

    private Product productEntity(UUID id) {
        return products.findById(id).orElseThrow(CatalogNotFoundException::new);
    }

    private Category activeCategory(UUID id) {
        Category value = categoryEntity(id);
        if (!value.isActive())
            throw new CatalogConflictException("CATEGORY_INACTIVE");
        return value;
    }

    private CatalogResult.Category category(Category value) {
        return new CatalogResult.Category(value.getId(), value.getCode(), value.getName(), value.isActive(),
                value.getCreatedAt(), value.getUpdatedAt());
    }

    private CatalogResult.Product product(Product value) {
        Category category = value.getCategory();
        return new CatalogResult.Product(value.getId(), value.getSku(), value.getName(), value.getDescription(),
                category.getId(), category.getCode(), category.getName(), value.getUnitOfMeasure(), value.isActive(),
                value.getCreatedAt(), value.getUpdatedAt());
    }

    private String normalizeQuery(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
