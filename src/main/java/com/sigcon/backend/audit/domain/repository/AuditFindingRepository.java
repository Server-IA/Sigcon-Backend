package com.sigcon.backend.audit.domain.repository;

import com.sigcon.backend.audit.domain.model.AuditFinding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AuditFindingRepository
        extends JpaRepository<AuditFinding, Long>, JpaSpecificationExecutor<AuditFinding> {

    List<AuditFinding> findByAuditLogIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long auditLogId);

    Page<AuditFinding> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    long countByStatusAndDeletedAtIsNull(String status);
}
