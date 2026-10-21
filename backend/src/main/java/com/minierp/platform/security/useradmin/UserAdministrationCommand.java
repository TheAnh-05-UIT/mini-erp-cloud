package com.minierp.platform.security.useradmin;

import java.util.Set;

import com.minierp.platform.security.persistence.RoleCode;

/** Defines explicit service inputs, never persistence entities. / Định nghĩa input service rõ ràng, không bao giờ nhận persistence entity. */
public final class UserAdministrationCommand {

    private UserAdministrationCommand() {
    }

    public record CreateUser(String username, String displayName, String initialPassword, Set<RoleCode> roleCodes) {
    }

    public record UpdateDisplayName(String displayName) {
    }

    public record ReplaceRoles(Set<RoleCode> roleCodes) {
    }
}
