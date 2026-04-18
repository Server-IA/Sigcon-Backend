package com.sigcon.backend.audit.domain.repository;

import com.sigcon.backend.audit.domain.model.AuditRiskRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditRiskRuleRepository extends JpaRepository<AuditRiskRule, Long> {

    /**
     * Reglas activas ordenadas por prioridad descendente. Usado por
     * {@code RiskRuleService} para evaluar la severidad de un evento.
     */
    List<AuditRiskRule> findByEnabledTrueOrderByPriorityDesc();
}
