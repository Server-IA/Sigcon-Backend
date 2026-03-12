package com.sigcon.backend.lists_accounting.depretation_rules.domain.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.DepretationRule;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationType;

@Repository
public interface DepretationRuleRepository extends JpaRepository<DepretationRule, Long>, 
        JpaSpecificationExecutor<DepretationRule> { 

            //validar Si hay duplicados (metodo + cuenta + vigencia)
            boolean existsByDepretationTypeAndAccountingAccountIdAndEffectiveDate(
                DepretationType depretationType, 
                Long accountingAccountId, 
                LocalDate effectiveDate
            );

            DepretationRule findByIdAndAccountingAccountId(Long id, Long accountingAccountId);
}
