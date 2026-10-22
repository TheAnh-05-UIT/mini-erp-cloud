package com.minierp.platform.catalog.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.minierp.platform.catalog.application.BusinessAuditRecord;

public interface BusinessAuditRecordRepository extends JpaRepository<BusinessAuditRecord, UUID> {
}
