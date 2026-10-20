package com.minierp.platform.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.minierp.platform.security.persistence.Role;
import com.minierp.platform.security.persistence.RoleCode;
import com.minierp.platform.security.persistence.UserAccount;
import com.minierp.platform.security.persistence.UserAccountRepository;
import com.minierp.platform.security.persistence.UserRole;

/**
 * Exercises authentication failure and audit failure semantics without a database. / Kiểm tra quy tắc thất bại của authentication và audit mà không cần database.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private SecurityAuthenticationAuditRecorder securityAuditRecorder;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                userAccountRepository,
                passwordEncoder,
                securityAuditRecorder);
    }

    @Test
    void authenticatesAnActiveUserWithMatchingPasswordAndActiveRole() {
        UserAccount user = authenticatedUser("sales.agent", RoleCode.SALES_STAFF, true);
        when(userAccountRepository.findActiveWithActiveRolesByUsername("sales.agent"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "bcrypt-hash")).thenReturn(true);

        AuthenticatedPrincipal principal = authenticationService.authenticate(" Sales.Agent ", "correct-password");

        assertThat(principal.username()).isEqualTo("sales.agent");
        assertThat(principal.authorities()).containsExactly(new SimpleGrantedAuthority("ROLE_SALES_STAFF"));
        verify(securityAuditRecorder).recordSuccessfulLogin(user);
    }

    @Test
    void usesBcryptCompatiblePasswordEncoderMatchesRatherThanDirectComparison() {
        String encoded = new BCryptPasswordEncoder(12).encode("correct-password");
        UserAccount user = authenticatedUser("manager", RoleCode.MANAGER, true);
        when(user.getPasswordHash()).thenReturn(encoded);
        when(userAccountRepository.findActiveWithActiveRolesByUsername("manager"))
                .thenReturn(Optional.of(user));

        AuthenticationService serviceWithBcrypt = new AuthenticationService(
                userAccountRepository,
                new BCryptPasswordEncoder(12),
                securityAuditRecorder);

        assertThat(serviceWithBcrypt.authenticate("manager", "correct-password").username()).isEqualTo("manager");
        assertThatThrownBy(() -> serviceWithBcrypt.authenticate("manager", "wrong-password"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Authentication failed");
    }

    @Test
    void deniesUnknownUsernameAndInvalidPasswordWithTheSameGenericFailure() {
        when(userAccountRepository.findActiveWithActiveRolesByUsername("unknown"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.authenticate("unknown", "wrong-password"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Authentication failed");

        UserAccount user = activeCredentialsUser();
        when(userAccountRepository.findActiveWithActiveRolesByUsername("admin"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.authenticate("admin", "wrong-password"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Authentication failed");
    }

    @Test
    void deniesInactiveUserEvenIfRepositoryReturnsOne() {
        UserAccount user = mock(UserAccount.class);
        when(user.isActive()).thenReturn(false);
        when(userAccountRepository.findActiveWithActiveRolesByUsername("inactive"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticationService.authenticate("inactive", "correct-password"))
                .isInstanceOf(AuthenticationFailedException.class);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void neverGrantsAnInactiveRole() {
        UserAccount user = authenticatedUser("manager", RoleCode.MANAGER, false);
        when(userAccountRepository.findActiveWithActiveRolesByUsername("manager"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "bcrypt-hash")).thenReturn(true);

        AuthenticatedPrincipal principal = authenticationService.authenticate("manager", "correct-password");

        assertThat(principal.authorities()).isEmpty();
    }

    @Test
    void failsClosedWhenSuccessfulLoginAuditCannotBePersisted() {
        UserAccount user = activeCredentialsUser();
        when(userAccountRepository.findActiveWithActiveRolesByUsername("admin"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "bcrypt-hash")).thenReturn(true);
        org.mockito.Mockito.doThrow(new SecurityAuditPersistenceException(new RuntimeException("database unavailable")))
                .when(securityAuditRecorder).recordSuccessfulLogin(user);

        assertThatThrownBy(() -> authenticationService.authenticate("admin", "correct-password"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Authentication failed");
    }

    @Test
    void failedLoginAuditFailureDoesNotChangeTheDeniedOutcome() {
        when(userAccountRepository.findActiveWithActiveRolesByUsername("unknown"))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new RuntimeException("audit unavailable"))
                .when(securityAuditRecorder).recordFailedLogin("unknown");

        assertThatThrownBy(() -> authenticationService.authenticate("unknown", "wrong-password"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Authentication failed");
    }

    private UserAccount activeCredentialsUser() {
        UserAccount user = mock(UserAccount.class);
        when(user.getPasswordHash()).thenReturn("bcrypt-hash");
        when(user.isActive()).thenReturn(true);
        return user;
    }

    private UserAccount authenticatedUser(String username, RoleCode roleCode, boolean roleActive) {
        UserAccount user = activeCredentialsUser();
        Role role = mock(Role.class);
        UserRole assignment = mock(UserRole.class);

        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getUsername()).thenReturn(username);
        when(user.getDisplayName()).thenReturn("Sales Agent");
        when(user.getRoleAssignments()).thenReturn(Set.of(assignment));
        when(assignment.getRole()).thenReturn(role);
        when(role.isActive()).thenReturn(roleActive);
        if (roleActive) {
            when(role.getCode()).thenReturn(roleCode);
        }
        return user;
    }
}
