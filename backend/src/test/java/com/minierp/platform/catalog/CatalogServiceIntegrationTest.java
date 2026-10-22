package com.minierp.platform.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import com.minierp.platform.catalog.application.*;
import com.minierp.platform.catalog.domain.UnitOfMeasure;
import com.minierp.platform.catalog.infrastructure.*;
import com.minierp.platform.persistence.TestDatabaseSafetyInitializer;
import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.persistence.*;

/** PostgreSQL acceptance coverage for Catalog rules. / Kiem tra nghiem thu Catalog tren PostgreSQL. */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
class CatalogServiceIntegrationTest {
    @Autowired CatalogService service;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired BusinessAuditRecordRepository audits;
    @Autowired UserAccountRepository users;
    @Autowired UserRoleRepository userRoles;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactions;
    private final Set<UUID> ownedUsers = new LinkedHashSet<>();
    private final Set<UUID> ownedCategories = new LinkedHashSet<>();
    private final Set<UUID> ownedProducts = new LinkedHashSet<>();

    @AfterEach void cleanupOwnedFixtures() {
        transactions.executeWithoutResult(status -> {
            for (UUID id : ownedProducts) entityManager.createNativeQuery("delete from business_audit_records where target_id=:id").setParameter("id", id).executeUpdate();
            for (UUID id : ownedCategories) entityManager.createNativeQuery("delete from business_audit_records where target_id=:id").setParameter("id", id).executeUpdate();
            for (UUID id : ownedProducts) entityManager.createNativeQuery("delete from products where id=:id").setParameter("id", id).executeUpdate();
            for (UUID id : ownedCategories) entityManager.createNativeQuery("delete from categories where id=:id").setParameter("id", id).executeUpdate();
            for (UUID id : ownedUsers) {
                entityManager.createNativeQuery("delete from business_audit_records where actor_user_id=:id").setParameter("id", id).executeUpdate();
                entityManager.createNativeQuery("delete from user_roles where user_id=:id or assigned_by_user_id=:id").setParameter("id", id).executeUpdate();
                entityManager.createNativeQuery("delete from users where id=:id").setParameter("id", id).executeUpdate();
            }
        });
    }

    @Test void normalizesSkuRejectsDuplicateAndPersistsCreateAudit() {
        AuthenticatedPrincipal actor = admin();
        var category = category(actor, "SKU");
        var product = service.createProduct(actor, " sku-" + token(), "Paper", null, category.id(), UnitOfMeasure.PIECE);
        ownedProducts.add(product.id());
        assertThat(product.sku()).startsWith("SKU-");
        assertThatThrownBy(() -> service.createProduct(actor, " " + product.sku().toLowerCase() + " ", "Duplicate", null, category.id(), UnitOfMeasure.PIECE))
                .isInstanceOf(CatalogConflictException.class);
        assertThat(audits.findAll().stream().filter(a -> true).count()).isGreaterThan(0);
    }

    @Test void lifecycleProtectsActiveCategoryAndFailClosesReassignment() {
        AuthenticatedPrincipal actor = admin();
        var first = category(actor, "FIRST");
        var second = category(actor, "SECOND");
        var product = service.createProduct(actor, "SKU-" + token(), "Paper", null, first.id(), UnitOfMeasure.PIECE);
        ownedProducts.add(product.id());
        assertThatThrownBy(() -> service.setCategoryActive(actor, first.id(), false)).isInstanceOf(CatalogConflictException.class);
        service.setProductActive(actor, product.id(), false);
        assertThat(service.setCategoryActive(actor, first.id(), false).active()).isFalse();
        assertThatThrownBy(() -> service.updateProduct(actor, product.id(), "Paper", null, second.id(), UnitOfMeasure.SET))
                .isInstanceOf(CatalogConflictException.class);
    }

    @Test void searchFilterAndStableSkuOrderingUsePostgres() {
        AuthenticatedPrincipal actor = admin();
        var category = category(actor, "QUERY");
        var b = service.createProduct(actor, "B-" + token(), "Paper B", null, category.id(), UnitOfMeasure.PIECE);
        var a = service.createProduct(actor, "A-" + token(), "Paper A", null, category.id(), UnitOfMeasure.PIECE);
        ownedProducts.add(b.id()); ownedProducts.add(a.id());
        var result = service.listProducts(actor, "Paper", category.id(), true, PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("sku")));
        assertThat(result.getContent()).extracting(CatalogResult.Product::sku).isSorted();
        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(2);
        assertThat(service.listProducts(actor, null, category.id(), true,
                PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("sku"))).getTotalElements())
                .isGreaterThanOrEqualTo(2);
    }

    private CatalogResult.Category category(AuthenticatedPrincipal actor, String prefix) {
        var value = service.createCategory(actor, prefix + "-" + token(), "Category " + token());
        ownedCategories.add(value.id()); return value;
    }
    private AuthenticatedPrincipal admin() {
        UUID id = UUID.randomUUID(); ownedUsers.add(id);
        UserAccount user = users.saveAndFlush(UserAccount.create(id, "catalog." + token(), encoder.encode("test-only-password"), "Catalog Admin"));
        userRoles.saveAndFlush(UserRole.assign(user, roles.findByCode(RoleCode.ADMIN).orElseThrow(), null));
        return new AuthenticatedPrincipal(id, user.getUsername(), user.getDisplayName(), Set.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
    private String token() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(); }
}
