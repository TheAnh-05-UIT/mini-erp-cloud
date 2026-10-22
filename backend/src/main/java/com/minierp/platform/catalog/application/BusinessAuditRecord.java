package com.minierp.platform.catalog.application;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import com.minierp.platform.security.persistence.UserAccount;

/**
 * Immutable business audit row, distinct from security audit. / Ban ghi audit
 * nghiep vu bat bien, tach khoi security audit.
 */
@Entity
@Table(name = "business_audit_records")
public class BusinessAuditRecord {
    @Id
    private UUID id;
    @Column(name = "module_code", nullable = false, length = 50)
    private String moduleCode;
    @Column(name = "action_code", nullable = false, length = 100)
    private String actionCode;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private UserAccount actor;
    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;
    @Column(name = "target_id", nullable = false)
    private UUID targetId;
    @Column(name = "target_reference", length = 100)
    private String targetReference;
    @Column(name = "from_status", length = 32)
    private String fromStatus;
    @Column(name = "to_status", length = 32)
    private String toStatus;
    @Column
    private String reason;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "correlation_reference", length = 100)
    private String correlationReference;

    protected BusinessAuditRecord() {
    }

    public static BusinessAuditRecord create(UUID id, UserAccount actor, String action, String type, UUID target,
            String reference, String from, String to) {
        BusinessAuditRecord value = new BusinessAuditRecord();
        value.id = id;
        value.actor = actor;
        value.moduleCode = "CATALOG";
        value.actionCode = action;
        value.targetType = type;
        value.targetId = target;
        value.targetReference = reference;
        value.fromStatus = from;
        value.toStatus = to;
        value.occurredAt = Instant.now();
        return value;
    }
}
