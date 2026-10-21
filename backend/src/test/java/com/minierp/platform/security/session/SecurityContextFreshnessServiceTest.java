package com.minierp.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.minierp.platform.security.persistence.Role;
import com.minierp.platform.security.persistence.RoleCode;
import com.minierp.platform.security.persistence.UserAccount;
import com.minierp.platform.security.persistence.UserAccountRepository;
import com.minierp.platform.security.persistence.UserRole;

/** Verifies that request-time authority data comes from the current persistence read. / Kiểm tra quyền mỗi request lấy từ lần đọc persistence hiện tại. */
class SecurityContextFreshnessServiceTest {

    @Test
    void loadsFreshActiveRolesInsteadOfTrustingTheSessionAuthorities() {
        UUID userId = UUID.randomUUID();
        UserAccountRepository repository = mock(UserAccountRepository.class);
        UserAccount user = mock(UserAccount.class);
        Role role = mock(Role.class);
        UserRole assignment = mock(UserRole.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("sales.agent");
        when(user.getDisplayName()).thenReturn("Sales Agent");
        when(user.isActive()).thenReturn(true);
        when(user.getRoleAssignments()).thenReturn(Set.of(assignment));
        when(assignment.getRole()).thenReturn(role);
        when(role.isActive()).thenReturn(true);
        when(role.getCode()).thenReturn(RoleCode.MANAGER);
        when(repository.findActiveWithActiveRolesById(userId)).thenReturn(Optional.of(user));

        var principal = new SecurityContextFreshnessService(repository).loadActivePrincipal(userId);

        assertThat(principal).isPresent();
        assertThat(principal.orElseThrow().authorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_MANAGER");
    }

    @Test
    void rejectsMissingOrInactiveUsersAtRefreshTime() {
        UUID missingUserId = UUID.randomUUID();
        UUID inactiveUserId = UUID.randomUUID();
        UserAccountRepository repository = mock(UserAccountRepository.class);
        UserAccount inactiveUser = mock(UserAccount.class);
        when(inactiveUser.isActive()).thenReturn(false);
        when(repository.findActiveWithActiveRolesById(missingUserId)).thenReturn(Optional.empty());
        when(repository.findActiveWithActiveRolesById(inactiveUserId)).thenReturn(Optional.of(inactiveUser));
        SecurityContextFreshnessService service = new SecurityContextFreshnessService(repository);

        assertThat(service.loadActivePrincipal(missingUserId)).isEmpty();
        assertThat(service.loadActivePrincipal(inactiveUserId)).isEmpty();
    }
}
