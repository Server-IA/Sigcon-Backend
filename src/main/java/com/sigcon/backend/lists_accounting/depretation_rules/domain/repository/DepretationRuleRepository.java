package com.sigcon.backend.lists_accounting.depretation_rules.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

            /**
             * HU-CFG-RF-15 E3: validar duplicidad en update excluyendo el id actual.
             */
            boolean existsByDepretationTypeAndAccountingAccountIdAndEffectiveDateAndIdNotAndDeletedAtIsNull(
                DepretationType depretationType,
                Long accountingAccountId,
                LocalDate effectiveDate,
                Long id
            );

            DepretationRule findByIdAndAccountingAccountId(Long id, Long accountingAccountId);

            Optional<DepretationRule> findById(Long id);

            List<DepretationRule> findByAccountingAccount_Id(Long accountingAccountId);

            /** HU-CFG-RF-13 E? (Bloque AP, 2026-05-04): nombre unico per-tenant.
             *  El @Filter("tenantFilter") restringe automaticamente al tenant. */
            boolean existsByNameAndDeletedAtIsNull(String name);

            /** HU-CFG-RF-15 E? (Bloque AP, 2026-05-04): nombre unico al editar
             *  excluyendo el id actual. */
            boolean existsByNameAndIdNotAndDeletedAtIsNull(String name, Long id);
}
