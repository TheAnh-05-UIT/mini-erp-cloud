package com.minierp.platform.security.web;

import java.util.Set;
import java.util.UUID;
import com.minierp.platform.security.persistence.RoleCode;
import com.minierp.platform.security.useradmin.UserAdministrationResult;

/** Safe user projection without credential data. / Projection user an toan, khong co du lieu credential. */
public record UserResponse(UUID id, String username, String displayName, boolean active, Set<RoleCode> roles) {
    static UserResponse from(UserAdministrationResult result) {
        return new UserResponse(result.id(), result.username(), result.displayName(), result.active(), result.roles());
    }
}
