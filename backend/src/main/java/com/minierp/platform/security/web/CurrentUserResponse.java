package com.minierp.platform.security.web;

import java.util.Set;
import java.util.UUID;

/** Safe current-user representation with no credential or session material. / Đại diện user hiện tại an toàn, không chứa credential hay session material. */
public record CurrentUserResponse(UUID id, String username, String displayName, Set<String> roles) {
}
