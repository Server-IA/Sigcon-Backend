package com.sigcon.backend.platform.audit.domain.repository;

import com.sigcon.backend.platform.audit.domain.model.PlatformAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformAuditLogRepository
        extends JpaRepository<PlatformAuditLog, Long>,
                JpaSpecificationExecutor<PlatformAuditLog> {
}
