package com.sigcon.backend.audit.domain.repository;

import com.sigcon.backend.audit.domain.model.AuditPurgeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditPurgeRecordRepository extends JpaRepository<AuditPurgeRecord, Long> {
    List<AuditPurgeRecord> findTop20ByOrderByPurgeDateDesc();
}
