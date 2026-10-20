package com.minierp.platform.security.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.minierp.platform.security.persistence.Role;
import com.minierp.platform.security.persistence.RoleCode;
import com.minierp.platform.security.persistence.RoleRepository;
import com.minierp.platform.security.persistence.SecurityAuditRecord;
import com.minierp.platform.security.persistence.SecurityAuditRecordRepository;
import com.minierp.platform.security.persistence.UserAccount;
import com.minierp.platform.security.persistence.UserAccountRepository;
import com.minierp.platform.security.persistence.UserRole;
import com.minierp.platform.security.persistence.UserRoleRepository;

/**
 * Verifies bootstrap decisions and rollback-facing failure paths in isolation. / Kiểm tra quyết định bootstrap và các nhánh lỗi dẫn đến rollback một cách độc lập.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    @Mock
    private PostgreSqlAdminBootstrapLock bootstrapLock;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private SecurityAuditRecordRepository securityAuditRecordRepository;

    @Mock
    private Role adminRole;

    private final AdminBootstrapProperties properties = new AdminBootstrapProperties();
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private AdminBootstrapService adminBootstrapService;

    @BeforeEach
    void setUp() {
        adminBootstrapService = new AdminBootstrapService(
                properties,
                bootstrapLock,
                userAccountRepository,
                roleRepository,
                userRoleRepository,
                securityAuditRecordRepository,
                passwordEncoder);
    }

    @Test
    void disabledBootstrapIsANoOpAndDoesNotRequireCredentials() {
        assertThat(adminBootstrapService.bootstrapIfEnabled()).isEqualTo(AdminBootstrapResult.DISABLED);

        verify(bootstrapLock, never()).acquire();
        verify(userAccountRepository, never()).count();
    }

    @Test
    void enabledBootstrapWithoutCredentialsFailsWithSafeMessage() {
        properties.setEnabled(true);

        assertThatThrownBy(() -> adminBootstrapService.bootstrapIfEnabled())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Admin bootstrap is enabled but username and password must be configured");
        verify(bootstrapLock, never()).acquire();
    }

    @Test
    void createsOneActiveAdminWithTheSeededAdminRoleAndAudit() {
        configureEnabledBootstrap();
        when(userAccountRepository.count()).thenReturn(0L);
        when(roleRepository.findByCode(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole));
        when(adminRole.isActive()).thenReturn(true);
        when(adminRole.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertThat(adminBootstrapService.bootstrapIfEnabled()).isEqualTo(AdminBootstrapResult.CREATED);

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).saveAndFlush(userCaptor.capture());
        UserAccount savedAdmin = userCaptor.getValue();
        assertThat(savedAdmin.getUsername()).isEqualTo("admin");
        assertThat(savedAdmin.isActive()).isTrue();
        assertThat(passwordEncoder.matches("test-only-bootstrap-password", savedAdmin.getPasswordHash())).isTrue();
        verify(userRoleRepository).saveAndFlush(any(UserRole.class));
        verify(securityAuditRecordRepository).saveAndFlush(any(SecurityAuditRecord.class));
    }

    @Test
    void existingUserPreventsBootstrapAndTheLockIsTakenBeforeTheGlobalCheck() {
        configureEnabledBootstrap();
        when(userAccountRepository.count()).thenReturn(1L);

        assertThat(adminBootstrapService.bootstrapIfEnabled())
                .isEqualTo(AdminBootstrapResult.SKIPPED_EXISTING_USER);

        InOrder order = inOrder(bootstrapLock, userAccountRepository);
        order.verify(bootstrapLock).acquire();
        order.verify(userAccountRepository).count();
        verify(userAccountRepository, never()).saveAndFlush(any(UserAccount.class));
    }

    @Test
    void auditPersistenceFailureIsPropagatedSoTransactionCanRollbackAllBootstrapWrites() {
        configureEnabledBootstrap();
        when(userAccountRepository.count()).thenReturn(0L);
        when(roleRepository.findByCode(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole));
        when(adminRole.isActive()).thenReturn(true);
        when(adminRole.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("audit unavailable"))
                .when(securityAuditRecordRepository).saveAndFlush(any(SecurityAuditRecord.class));

        assertThatThrownBy(() -> adminBootstrapService.bootstrapIfEnabled())
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    private void configureEnabledBootstrap() {
        properties.setEnabled(true);
        properties.setUsername(" Admin ");
        properties.setPassword("test-only-bootstrap-password");
    }
}
