package com.minierp.platform.security.useradmin;

import java.util.Map;

import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.persistence.UserAccount;

/** Persists mandatory User Administration audit inside the caller transaction. / Lưu audit bắt buộc trong transaction của User Administration. */
interface UserAdministrationAuditRecorder {
    void record(AuthenticatedPrincipal actor, UserAccount target, String eventType, Map<String, String> metadata);
}
