package com.minierp.platform.security.bootstrap;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minierp.platform.security.persistence.Role;
import com.minierp.platform.security.persistence.RoleCode;
import com.minierp.platform.security.persistence.RoleRepository;
import com.minierp.platform.security.persistence.SecurityAuditOutcome;
import com.minierp.platform.security.persistence.SecurityAuditRecord;
import com.minierp.platform.security.persistence.SecurityAuditRecordRepository;
import com.minierp.platform.security.persistence.UserAccount;
import com.minierp.platform.security.persistence.UserAccountRepository;
import com.minierp.platform.security.persistence.UserRole;
import com.minierp.platform.security.persistence.UserRoleRepository;

/**
 * Creates exactly one initial Admin only when explicitly enabled and the user store is empty.
 * Chỉ tạo một Admin ban đầu khi được bật rõ ràng và kho người dùng đang trống.
 */
@Service
public class AdminBootstrapService {

    private static final String ADMIN_BOOTSTRAP_EVENT = "ADMIN_BOOTSTRAP";

    private final AdminBootstrapProperties properties;
    private final PostgreSqlAdminBootstrapLock bootstrapLock;
    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final SecurityAuditRecordRepository securityAuditRecordRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapService(
            AdminBootstrapProperties properties,
            PostgreSqlAdminBootstrapLock bootstrapLock,
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            SecurityAuditRecordRepository securityAuditRecordRepository,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.bootstrapLock = bootstrapLock;
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.securityAuditRecordRepository = securityAuditRecordRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AdminBootstrapResult bootstrapIfEnabled() {
        if (!properties.isEnabled()) {
            return AdminBootstrapResult.DISABLED;
        }

        validateConfiguration();
        bootstrapLock.acquire();

        if (userAccountRepository.count() > 0) {
            return AdminBootstrapResult.SKIPPED_EXISTING_USER;
        }

        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN)
                .filter(Role::isActive)
                .orElseThrow(() -> new IllegalStateException("Required ADMIN role is unavailable"));

        UserAccount admin = UserAccount.create(
                UUID.randomUUID(),
                properties.getUsername(),
                passwordEncoder.encode(properties.getPassword()),
                "Bootstrap Administrator");
        userAccountRepository.saveAndFlush(admin);
        userRoleRepository.saveAndFlush(UserRole.assign(admin, adminRole, null));
        securityAuditRecordRepository.saveAndFlush(SecurityAuditRecord.create(
                UUID.randomUUID(),
                null,
                admin,
                ADMIN_BOOTSTRAP_EVENT,
                SecurityAuditOutcome.SUCCESS,
                admin.getId().toString(),
                null,
                Map.of("bootstrap", "one-time-admin")));

        return AdminBootstrapResult.CREATED;
    }

    private void validateConfiguration() {
        if (isBlank(properties.getUsername()) || isBlank(properties.getPassword())) {
            throw new IllegalStateException("Admin bootstrap is enabled but username and password must be configured");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
