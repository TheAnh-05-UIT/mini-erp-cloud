package com.minierp.platform.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Set; import java.util.UUID;
import org.junit.jupiter.api.Test; import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.minierp.platform.catalog.application.*; import com.minierp.platform.security.authentication.AuthenticatedPrincipal;

/** Verifies explicit, default-deny catalog capability mapping. / Kiem tra ma tran quyen catalog ro rang va mac dinh tu choi. */
class CatalogAuthorizationServiceTest {
    private final CatalogAuthorizationService service = new CatalogAuthorizationService();
    @Test void purchasingCanCreateButCannotDeactivateProduct() { var actor=principal("PURCHASING_STAFF"); assertThat(service.has(actor,CatalogPermission.PRODUCT_CREATE)).isTrue(); assertThat(service.has(actor,CatalogPermission.PRODUCT_DEACTIVATE)).isFalse(); }
    @Test void readerRolesCanReadOnly() { var actor=principal("SALES_STAFF"); assertThat(service.has(actor,CatalogPermission.PRODUCT_READ)).isTrue(); assertThat(service.has(actor,CatalogPermission.CATEGORY_UPDATE)).isFalse(); }
    @Test void adminCapabilitiesAreExplicitAndUnknownRoleIsDenied() { assertThat(service.has(principal("ADMIN"),CatalogPermission.CATEGORY_DEACTIVATE)).isTrue(); assertThat(service.has(principal("UNKNOWN"),CatalogPermission.PRODUCT_READ)).isFalse(); }
    private AuthenticatedPrincipal principal(String role){return new AuthenticatedPrincipal(UUID.randomUUID(),"user","User",Set.of(new SimpleGrantedAuthority("ROLE_"+role)));}
}
