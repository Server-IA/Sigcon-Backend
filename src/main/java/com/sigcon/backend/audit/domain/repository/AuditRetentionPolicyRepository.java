package com.sigcon.backend.audit.domain.repository;

import com.sigcon.backend.audit.domain.model.AuditRetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditRetentionPolicyRepository extends JpaRepository<AuditRetentionPolicy, Long> {
    List<AuditRetentionPolicy> findByEnabledTrue();
}
