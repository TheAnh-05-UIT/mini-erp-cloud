package com.minierp.platform.security.authentication;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.minierp.platform.security.persistence.Role;
import com.minierp.platform.security.persistence.UserAccount;
import com.minierp.platform.security.persistence.UserAccountRepository;
import com.minierp.platform.security.persistence.UserRole;

/**
 * Verifies database-backed credentials and active roles, but does not create an HTTP session.
 * Xác minh thông tin đăng nhập và role trong cơ sở dữ liệu, nhưng không tạo HTTP session.
 */
@Service
public class AuthenticationService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuthenticationAuditRecorder securityAuditRecorder;

    public AuthenticationService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            SecurityAuthenticationAuditRecorder securityAuditRecorder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityAuditRecorder = securityAuditRecorder;
    }

    /**
     * Verifies credentials and returns identity/authority data only. It does not
     * create an HTTP session or expose a REST-facing response.
     */
    public AuthenticatedPrincipal authenticate(String submittedUsername, String rawPassword) {
        String normalizedUsername = normalizeForLookup(submittedUsername);
        UserAccount user = userAccountRepository.findActiveWithActiveRolesByUsername(normalizedUsername)
                .filter(UserAccount::isActive)
                .filter(candidate -> passwordMatches(rawPassword, candidate.getPasswordHash()))
                .orElseGet(() -> deny(normalizedUsername));

        try {
            securityAuditRecorder.recordSuccessfulLogin(user);
        } catch (RuntimeException exception) {
            // Successful authentication is fail-closed when its required audit fails.
            throw new AuthenticationFailedException();
        }
        return new AuthenticatedPrincipal(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                activeAuthorities(user));
    }

    private String normalizeForLookup(String submittedUsername) {
        try {
            return UserAccount.normalizeUsername(submittedUsername);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private boolean passwordMatches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null) {
            return false;
        }

        try {
            return passwordEncoder.matches(rawPassword, passwordHash);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private UserAccount deny(String normalizedUsername) {
        try {
            securityAuditRecorder.recordFailedLogin(normalizedUsername);
        } catch (RuntimeException exception) {
            // A failed audit must not turn an already-denied attempt into a success.
        }
        throw new AuthenticationFailedException();
    }

    private Set<GrantedAuthority> activeAuthorities(UserAccount user) {
        return user.getRoleAssignments().stream()
                .map(UserRole::getRole)
                .filter(Role::isActive)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.getCode().name()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
