package com.minierp.platform.security.session;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.persistence.Role;
import com.minierp.platform.security.persistence.UserAccount;
import com.minierp.platform.security.persistence.UserAccountRepository;
import com.minierp.platform.security.persistence.UserRole;

/** Reloads active user authorities for every protected request. / Tải lại user và role hợp lệ cho mỗi protected request. */
@Service
public class SecurityContextFreshnessService {

    private final UserAccountRepository userAccountRepository;

    public SecurityContextFreshnessService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public Optional<AuthenticatedPrincipal> loadActivePrincipal(UUID userId) {
        return userAccountRepository.findActiveWithActiveRolesById(userId)
                .filter(UserAccount::isActive)
                .map(this::toPrincipal);
    }

    private AuthenticatedPrincipal toPrincipal(UserAccount user) {
        Set<GrantedAuthority> authorities = user.getRoleAssignments().stream()
                .map(UserRole::getRole)
                .filter(Role::isActive)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.getCode().name()))
                .collect(Collectors.toUnmodifiableSet());
        return new AuthenticatedPrincipal(user.getId(), user.getUsername(), user.getDisplayName(), authorities);
    }
}
