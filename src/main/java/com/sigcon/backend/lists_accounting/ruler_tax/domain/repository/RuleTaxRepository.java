package com.sigcon.backend.lists_accounting.ruler_tax.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.TaxRulerEntity;

public interface RuleTaxRepository extends JpaRepository<TaxRulerEntity, Long>, JpaSpecificationExecutor<TaxRulerEntity> {

    // Page<TaxRulerEntity> findAll(Specification<TaxRulerEntity> specification, Pageable pageable);

    Optional<TaxRulerEntity> findById(Long id);

    List<TaxRulerEntity> findByAccountingAccountId(Long accountingAccountId);

    // HU-CFG-RF-09 E3: validar unicidad de nombre.
    boolean existsByNameAndDeletedAtIsNull(String name);

    // HU-CFG-RF-11 E3: validar unicidad excluyendo el id actual al editar.
    boolean existsByNameAndIdNotAndDeletedAtIsNull(String name, Long id);

    // QA Bloque AU+ HU-AP-06 E2 (2026-05-06): contar reglas tributarias ACTIVE
    // en el tenant. Si retorna 0, la creacion de facturas debe bloquearse con
    // mensaje "No hay reglas tributarias activas...". @Filter(tenantFilter)
    // hace que la cuenta sea por empresa automaticamente.
    long countByStatusAndDeletedAtIsNull(com.sigcon.backend.lists_accounting.ruler_tax.domain.model.enums.StatusRulerTax status);

}
