package com.minierp.platform.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import com.minierp.platform.catalog.application.CatalogService;
import com.minierp.platform.catalog.domain.Category;
import com.minierp.platform.catalog.domain.Product;
import com.minierp.platform.catalog.domain.UnitOfMeasure;
import com.minierp.platform.catalog.infrastructure.BusinessAuditRecordRepository;
import com.minierp.platform.catalog.infrastructure.CategoryRepository;
import com.minierp.platform.catalog.infrastructure.ProductRepository;
import com.minierp.platform.persistence.TestDatabaseSafetyInitializer;
import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.persistence.*;

/** Proves Catalog writes roll back when mandatory business audit fails. / Chung minh mutation Catalog rollback khi audit bat buoc that bai. */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
class CatalogAuditRollbackIntegrationTest {
    @Autowired CatalogService service;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired UserAccountRepository users;
    @Autowired UserRoleRepository userRoles;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactions;
    @MockitoBean BusinessAuditRecordRepository audits;
    private UUID userId;
    private UUID categoryId;
    private UUID productId;

    @Test void productLifecycleMutationRollsBackWhenBusinessAuditFails() {
        AuthenticatedPrincipal actor = fixture();
        when(audits.save(any())).thenThrow(new AuditFailure());
        assertThatThrownBy(() -> service.setProductActive(actor, productId, false)).isInstanceOf(AuditFailure.class);
        Boolean committedActive = transactions.execute(status -> (Boolean) entityManager
                .createNativeQuery("select active from products where id=:id").setParameter("id", productId).getSingleResult());
        Number committedAuditCount = transactions.execute(status -> (Number) entityManager
                .createNativeQuery("select count(*) from business_audit_records where target_id=:id").setParameter("id", productId).getSingleResult());
        assertThat(committedActive).isTrue();
        assertThat(committedAuditCount.longValue()).isZero();
    }

    @AfterEach void cleanupOwnedFixtures() {
        transactions.executeWithoutResult(status -> {
            if (productId != null) entityManager.createNativeQuery("delete from products where id=:id").setParameter("id", productId).executeUpdate();
            if (categoryId != null) entityManager.createNativeQuery("delete from categories where id=:id").setParameter("id", categoryId).executeUpdate();
            if (userId != null) {
                entityManager.createNativeQuery("delete from user_roles where user_id=:id").setParameter("id", userId).executeUpdate();
                entityManager.createNativeQuery("delete from users where id=:id").setParameter("id", userId).executeUpdate();
            }
        });
    }

    private AuthenticatedPrincipal fixture() {
        userId = UUID.randomUUID(); categoryId = UUID.randomUUID(); productId = UUID.randomUUID();
        UserAccount user = users.saveAndFlush(UserAccount.create(userId, "catalog.rollback." + userId,
                encoder.encode("test-only-password"), "Catalog rollback admin"));
        userRoles.saveAndFlush(UserRole.assign(user, roles.findByCode(RoleCode.ADMIN).orElseThrow(), null));
        Category category = categories.saveAndFlush(Category.create(categoryId, "ROLLBACK", "Rollback category"));
        products.saveAndFlush(Product.create(productId, "ROLLBACK-" + productId.toString().substring(0, 8), "Rollback product", null, category, UnitOfMeasure.PIECE));
        return new AuthenticatedPrincipal(userId, user.getUsername(), user.getDisplayName(), Set.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
    static final class AuditFailure extends RuntimeException { }
}
