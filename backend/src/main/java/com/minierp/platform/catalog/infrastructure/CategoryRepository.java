package com.minierp.platform.catalog.infrastructure;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.minierp.platform.catalog.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    boolean existsByCode(String code);

    @Query("select case when count(c)>0 then true else false end from Category c where lower(c.name)=lower(:name)")
    boolean existsByNormalizedName(String name);

    boolean existsByIdAndActiveTrue(UUID id);

    @Query("select c from Category c where (:active is null or c.active=:active) and (coalesce(:q, '') = '' or lower(c.code) like lower(concat(coalesce(:q, ''), '%')) or lower(c.name) like lower(concat(coalesce(:q, ''), '%')))")
    Page<Category> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);
}
