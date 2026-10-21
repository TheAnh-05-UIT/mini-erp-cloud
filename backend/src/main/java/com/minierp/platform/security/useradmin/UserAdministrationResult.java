package com.minierp.platform.security.useradmin;

import java.util.Set;
import java.util.UUID;

import com.minierp.platform.security.persistence.RoleCode;

/** Returns a safe service projection without credential or session data. / Trả projection service an toàn, không chứa credential hay session. */
public record UserAdministrationResult(UUID id, String username, String displayName, boolean active, Set<RoleCode> roles) {
}
