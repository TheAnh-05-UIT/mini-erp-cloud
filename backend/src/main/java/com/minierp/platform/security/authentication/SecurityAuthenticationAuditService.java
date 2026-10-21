package com.minierp.platform.security.authentication;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minierp.platform.security.persistence.SecurityAuditOutcome;
import com.minierp.platform.security.persistence.SecurityAuditRecord;
import com.minierp.platform.security.persistence.SecurityAuditRecordRepository;
import com.minierp.platform.security.persistence.UserAccount;

/**
 * Persists allowlisted authentication events and never records credentials or session secrets.
 * Lưu event xác thực đã được cho phép và không ghi credentials hay session secrets.
 */
@Service
class SecurityAuthenticationAuditService implements SecurityAuthenticationAuditRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityAuthenticationAuditService.class);
    private static final String LOGIN_EVENT_TYPE = "LOGIN";
    private static final String LOGOUT_EVENT_TYPE = "LOGOUT";

    private final SecurityAuditRecordRepository securityAuditRecordRepository;

    SecurityAuthenticationAuditService(SecurityAuditRecordRepository securityAuditRecordRepository) {
        this.securityAuditRecordRepository = securityAuditRecordRepository;
    }

    /**
     * A successful credential verification is not returned to the caller until its
     * required security audit record has been flushed in the successful
     * authentication transaction.
     */
    @Override
    @Transactional
    public void recordSuccessfulLogin(UserAccount user) {
        try {
            securityAuditRecordRepository.saveAndFlush(SecurityAuditRecord.create(
                    UUID.randomUUID(),
                    user,
                    user,
                    LOGIN_EVENT_TYPE,
                    SecurityAuditOutcome.SUCCESS,
                    user.getId().toString(),
                    null,
                    Map.of("authentication", "password")));
        } catch (DataAccessException exception) {
            throw new SecurityAuditPersistenceException(exception);
        }
    }

    /**
     * A failed login remains denied when the best-effort failure audit cannot be
     * stored. The log deliberately excludes username and credential material.
     */
    @Override
    public void recordFailedLogin(String normalizedUsername) {
        try {
            securityAuditRecordRepository.saveAndFlush(SecurityAuditRecord.create(
                    UUID.randomUUID(),
                    null,
                    null,
                    LOGIN_EVENT_TYPE,
                    SecurityAuditOutcome.FAILURE,
                    normalizedUsername,
                    null,
                    Map.of("authentication", "password")));
        } catch (DataAccessException exception) {
            LOGGER.warn("Security audit persistence failed for a denied authentication attempt");
        }
    }

    /**
     * Logout remains effective even when its post-invalidation audit cannot be
     * stored. The fallback log contains no user identifier or session data.
     */
    @Override
    public void recordLogout(UUID userId) {
        try {
            securityAuditRecordRepository.saveAndFlush(SecurityAuditRecord.create(
                    UUID.randomUUID(),
                    null,
                    null,
                    LOGOUT_EVENT_TYPE,
                    SecurityAuditOutcome.SUCCESS,
                    userId.toString(),
                    null,
                    null));
        } catch (DataAccessException exception) {
            LOGGER.warn("Security audit persistence failed after session invalidation");
        }
    }
}
