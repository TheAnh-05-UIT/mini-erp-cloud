package com.minierp.platform.security.authentication;

import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;

/**
 * Verified identity data for later conversion into a Spring Security
 * Authentication and HTTP session. It deliberately contains no credential data.
 */
/**
 * Carries the verified active user and usable role codes, without credential data.
 * Mang người dùng đang hoạt động và role hợp lệ, không chứa thông tin xác thực.
 */
public record AuthenticatedPrincipal(
        UUID userId,
        String username,
        String displayName,
        Set<GrantedAuthority> authorities) {

    public AuthenticatedPrincipal {
        authorities = Set.copyOf(authorities);
    }
}
