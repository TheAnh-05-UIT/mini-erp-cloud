package com.minierp.platform.catalog.web;

import java.net.URI;
import java.util.Set;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.minierp.platform.catalog.application.*;
import com.minierp.platform.catalog.web.CatalogRequests.*;
import com.minierp.platform.catalog.web.CatalogResponses.*;
import com.minierp.platform.security.authentication.AuthenticatedPrincipal;

/** HTTP catalog adapter. / Adapter HTTP catalog. */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public PageResponse<Category> categories(Authentication auth, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active) {
        return PageResponse.from(service.listCategories(principal(auth), q, active,
                page(page, size, "code", Set.of("code", "name", "createdAt"))),
                Category::from);
    }

    @PostMapping("/categories")
    public ResponseEntity<Category> createCategory(Authentication auth, @Valid @RequestBody CreateCategory request) {
        Category result = Category.from(service.createCategory(principal(auth), request.code(), request.name()));
        return ResponseEntity.created(URI.create("/api/v1/categories/" + result.id())).body(result);
    }

    @GetMapping("/categories/{id}")
    public Category category(Authentication auth, @PathVariable UUID id) {
        return Category.from(service.category(principal(auth), id));
    }

    @PatchMapping("/categories/{id}")
    public Category updateCategory(Authentication auth, @PathVariable UUID id,
            @Valid @RequestBody UpdateCategory request) {
        return Category.from(service.updateCategory(principal(auth), id, request.name()));
    }

    @PostMapping("/categories/{id}/activate")
    public Category activateCategory(Authentication auth, @PathVariable UUID id) {
        return Category.from(service.setCategoryActive(principal(auth), id, true));
    }

    @PostMapping("/categories/{id}/deactivate")
    public Category deactivateCategory(Authentication auth, @PathVariable UUID id) {
        return Category.from(service.setCategoryActive(principal(auth), id, false));
    }

    @GetMapping("/products")
    public PageResponse<Product> products(Authentication auth, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId, @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "sku") String sort) {
        return PageResponse.from(service.listProducts(principal(auth), q, categoryId, active,
                page(page, size, sort, Set.of("sku", "name", "createdAt"))),
                Product::from);
    }

    @PostMapping("/products")
    public ResponseEntity<Product> createProduct(Authentication auth, @Valid @RequestBody CreateProduct request) {
        Product result = Product.from(service.createProduct(principal(auth), request.sku(), request.name(),
                request.description(), request.categoryId(), request.unitOfMeasure()));
        return ResponseEntity.created(URI.create("/api/v1/products/" + result.id())).body(result);
    }

    @GetMapping("/products/{id}")
    public Product product(Authentication auth, @PathVariable UUID id) {
        return Product.from(service.product(principal(auth), id));
    }

    @PatchMapping("/products/{id}")
    public Product updateProduct(Authentication auth, @PathVariable UUID id,
            @Valid @RequestBody UpdateProduct request) {
        return Product.from(service.updateProduct(principal(auth), id, request.name(), request.description(),
                request.categoryId(), request.unitOfMeasure()));
    }

    @PostMapping("/products/{id}/activate")
    public Product activateProduct(Authentication auth, @PathVariable UUID id) {
        return Product.from(service.setProductActive(principal(auth), id, true));
    }

    @PostMapping("/products/{id}/deactivate")
    public Product deactivateProduct(Authentication auth, @PathVariable UUID id) {
        return Product.from(service.setProductActive(principal(auth), id, false));
    }

    private AuthenticatedPrincipal principal(Authentication auth) {
        return (AuthenticatedPrincipal) auth.getPrincipal();
    }

    private Pageable page(int page, int size, String sort, Set<String> allowedSorts) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("Invalid pagination");
        if (!allowedSorts.contains(sort))
            throw new IllegalArgumentException("Invalid sort");
        return PageRequest.of(page, size, Sort.by(sort).ascending());
    }
}
