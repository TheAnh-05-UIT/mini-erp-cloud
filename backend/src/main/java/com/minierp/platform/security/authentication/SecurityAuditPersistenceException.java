package com.minierp.platform.security.authentication;

/** Signals that a required successful-login audit record could not be persisted. */
/**
 * Signals that a required security audit could not be persisted. / Báo hiệu security audit bắt buộc không thể được lưu.
 */
public class SecurityAuditPersistenceException extends RuntimeException {

    public SecurityAuditPersistenceException(Throwable cause) {
        super("Security audit persistence failed", cause);
    }
}
