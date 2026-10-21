package com.minierp.platform.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.persistence.RoleCode;

class UserAdministrationAuthorizationServiceTest {

    private UserAdministrationAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new UserAdministrationAuthorizationService(new RolePermissionMatrix());
    }

    @Test
    void adminHasEveryApprovedUserAdministrationPermission() {
        AuthenticatedPrincipal admin = principal(RoleCode.ADMIN);

        Arrays.stream(UserAdministrationPermission.values())
                .forEach(permission -> assertThat(authorizationService.hasPermission(admin, permission)).isTrue());
    }

    @Test
    void managerIsDeniedUntilItsProposedReadOnlyPolicyIsExplicitlyApproved() {
        AuthenticatedPrincipal manager = principal(RoleCode.MANAGER);

        assertThat(Arrays.stream(UserAdministrationPermission.values())
                .noneMatch(permission -> authorizationService.hasPermission(manager, permission)))
                .isTrue();
    }

    @Test
    void operationalRolesReceiveNoUserAdministrationPermission() {
        for (RoleCode role : Set.of(RoleCode.PURCHASING_STAFF, RoleCode.WAREHOUSE_STAFF, RoleCode.SALES_STAFF)) {
            assertThat(Arrays.stream(UserAdministrationPermission.values())
                    .noneMatch(permission -> authorizationService.hasPermission(principal(role), permission)))
                    .isTrue();
        }
    }

    @Test
    void multipleRolesUseOnlyTheUnionOfExplicitlyGrantedPermissions() {
        AuthenticatedPrincipal principal = principal(RoleCode.SALES_STAFF, RoleCode.ADMIN);

        assertThat(authorizationService.hasPermission(principal, UserAdministrationPermission.USER_ASSIGN_ROLES)).isTrue();
    }

    @Test
    void inactiveOrUnknownAuthoritiesDoNotGrantPermissions() {
        AuthenticatedPrincipal principal = principalFromAuthorities("ROLE_UNKNOWN", "ROLE_INACTIVE_MANAGER");

        assertThat(authorizationService.hasPermission(principal, UserAdministrationPermission.USER_READ)).isFalse();
    }

    @Test
    void unknownOrMissingPermissionIsDeniedByDefault() {
        assertThat(authorizationService.hasPermission(principal(RoleCode.ADMIN), null)).isFalse();
        assertThatThrownBy(() -> authorizationService.requirePermission(principal(RoleCode.SALES_STAFF), UserAdministrationPermission.USER_READ))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void refreshedAuthoritiesImmediatelyChangeTheCapabilityDecision() {
        AuthenticatedPrincipal beforeRefresh = principal(RoleCode.SALES_STAFF);
        AuthenticatedPrincipal afterRefresh = principal(RoleCode.ADMIN);

        assertThat(authorizationService.hasPermission(beforeRefresh, UserAdministrationPermission.USER_CREATE)).isFalse();
        assertThat(authorizationService.hasPermission(afterRefresh, UserAdministrationPermission.USER_CREATE)).isTrue();
    }

    private AuthenticatedPrincipal principal(RoleCode... roles) {
        return principalFromAuthorities(Arrays.stream(roles).map(role -> "ROLE_" + role.name()).toArray(String[]::new));
    }

    private AuthenticatedPrincipal principalFromAuthorities(String... authorities) {
        return new AuthenticatedPrincipal(
                UUID.randomUUID(),
                "user",
                "User",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }
}
