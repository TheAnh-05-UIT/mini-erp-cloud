package com.minierp.platform.security.persistence;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Stores redacted security-event history, separate from business audit and operational logs.
 * Lưu lịch sử security event đã được lọc, tách biệt business audit và operational logs.
 */
@Entity
@Table(name = "security_audit_records")
public class SecurityAuditRecord {

    private static final Set<String> SENSITIVE_METADATA_KEY_PARTS = Set.of(
            "authorization",
            "cookie",
            "csrf",
            "hash",
            "password",
            "secret",
            "token");

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private UserAccount actorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private UserAccount targetUser;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SecurityAuditOutcome outcome;

    @Column(name = "target_reference", length = 100)
    private String targetReference;

    @Column(name = "correlation_reference", length = 100)
    private String correlationReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> metadata;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected SecurityAuditRecord() {
    }

    public static SecurityAuditRecord create(
            UUID id,
            UserAccount actorUser,
            UserAccount targetUser,
            String eventType,
            SecurityAuditOutcome outcome,
            String targetReference,
            String correlationReference,
            Map<String, String> metadata) {
        SecurityAuditRecord record = new SecurityAuditRecord();
        record.id = requireId(id);
        record.actorUser = actorUser;
        record.targetUser = targetUser;
        record.eventType = requireNonBlank(eventType, "eventType");
        record.outcome = requireOutcome(outcome);
        record.targetReference = requireBounded(targetReference, "targetReference");
        record.correlationReference = requireBounded(correlationReference, "correlationReference");
        record.metadata = sanitizeMetadata(metadata);
        return record;
    }

    public UUID getId() {
        return id;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    @PrePersist
    void initializeOccurredAt() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    private static UUID requireId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("id is required");
        }
        return value;
    }

    private static SecurityAuditOutcome requireOutcome(SecurityAuditOutcome value) {
        if (value == null) {
            throw new IllegalArgumentException("outcome is required");
        }
        return value;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static String requireBounded(String value, String fieldName) {
        if (value != null && value.length() > 100) {
            throw new IllegalArgumentException(fieldName + " must not exceed 100 characters");
        }
        return value;
    }

    private static Map<String, String> sanitizeMetadata(Map<String, String> value) {
        if (value == null) {
            return null;
        }

        value.keySet().forEach(SecurityAuditRecord::rejectSensitiveMetadataKey);
        return Map.copyOf(value);
    }

    private static void rejectSensitiveMetadataKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("metadata keys must be nonblank");
        }

        String normalizedKey = key.toLowerCase(Locale.ROOT);
        if (SENSITIVE_METADATA_KEY_PARTS.stream().anyMatch(normalizedKey::contains)) {
            throw new IllegalArgumentException("metadata must not contain secrets or authentication material");
        }
    }
}
