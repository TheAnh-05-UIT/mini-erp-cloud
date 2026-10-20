package com.minierp.platform.security.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists durable security audit records required by security workflows. / Lưu security audit record bền vững bắt buộc cho các luồng bảo mật.
 */
public interface SecurityAuditRecordRepository extends JpaRepository<SecurityAuditRecord, UUID> {

    long countByEventTypeAndOutcome(String eventType, SecurityAuditOutcome outcome);
}
