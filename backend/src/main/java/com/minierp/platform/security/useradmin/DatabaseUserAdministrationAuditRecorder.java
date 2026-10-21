package com.minierp.platform.security.useradmin;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.persistence.SecurityAuditOutcome;
import com.minierp.platform.security.persistence.SecurityAuditRecord;
import com.minierp.platform.security.persistence.SecurityAuditRecordRepository;
import com.minierp.platform.security.persistence.UserAccount;
import com.minierp.platform.security.persistence.UserAccountRepository;

/** Writes required audit rows without creating a separate transaction. / Ghi audit bắt buộc mà không tạo transaction riêng. */
@Service
class DatabaseUserAdministrationAuditRecorder implements UserAdministrationAuditRecorder {
    private final SecurityAuditRecordRepository audits;
    private final UserAccountRepository users;
    DatabaseUserAdministrationAuditRecorder(SecurityAuditRecordRepository audits, UserAccountRepository users) { this.audits = audits; this.users = users; }
    @Override public void record(AuthenticatedPrincipal actor, UserAccount target, String eventType, Map<String, String> metadata) {
        audits.saveAndFlush(SecurityAuditRecord.create(UUID.randomUUID(), users.getReferenceById(actor.userId()), target,
                eventType, SecurityAuditOutcome.SUCCESS, target.getId().toString(), null, metadata));
    }
}
