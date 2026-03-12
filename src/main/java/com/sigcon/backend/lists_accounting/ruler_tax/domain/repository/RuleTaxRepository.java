package com.sigcon.backend.lists_accounting.ruler_tax.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.TaxRulerEntity;

public interface RuleTaxRepository extends JpaRepository<TaxRulerEntity, Long>, JpaSpecificationExecutor<TaxRulerEntity> {

    // Page<TaxRulerEntity> findAll(Specification<TaxRulerEntity> specification, Pageable pageable);

}
