package com.sigcon.backend.nomina.domain.repository;

import com.sigcon.backend.nomina.domain.model.PayrollConcept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de conceptos de nomina (HU-NOM-02).
 */
public interface PayrollConceptRepository extends JpaRepository<PayrollConcept, Long>, JpaSpecificationExecutor<PayrollConcept> {

    Optional<PayrollConcept> findByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    /** Conceptos ACTIVE por tipo (EARNING/DEDUCTION/EMPLOYER_CONTRIBUTION). */
    List<PayrollConcept> findByConceptTypeAndStatusAndDeletedAtIsNullOrderByCode(String conceptType, String status);

    /** Todos los conceptos activos. */
    List<PayrollConcept> findByStatusAndDeletedAtIsNullOrderByConceptTypeAscCodeAsc(String status);
}
