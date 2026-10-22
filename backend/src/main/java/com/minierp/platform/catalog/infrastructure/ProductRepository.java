package com.minierp.platform.catalog.infrastructure;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.minierp.platform.catalog.domain.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    boolean existsBySku(String sku);

    boolean existsByCategoryIdAndActiveTrue(UUID categoryId);

    @Query("select p from Product p where (:active is null or p.active=:active) and (:categoryId is null or p.category.id=:categoryId) and (coalesce(:q, '') = '' or lower(p.sku) like lower(concat(coalesce(:q, ''), '%')) or lower(p.name) like lower(concat(coalesce(:q, ''), '%')))")
    Page<Product> search(@Param("q") String q, @Param("categoryId") UUID categoryId, @Param("active") Boolean active,
            Pageable pageable);
}
