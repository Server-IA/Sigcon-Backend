package com.sigcon.backend.banks.banks.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.banks.banks.domain.model.Bank;

import java.util.List;
import java.util.Optional;

public interface BankRepository extends JpaRepository<Bank, Long>, JpaSpecificationExecutor<Bank> {

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndIdNotAndDeletedAtIsNull(String code, Long id);

    boolean existsByNameAndDeletedAtIsNull(String name);

    boolean existsByNameAndIdNotAndDeletedAtIsNull(String name, Long id);

    boolean existsByNitAndDeletedAtIsNull(String nit);

    boolean existsByNitAndIdNotAndDeletedAtIsNull(String nit, Long id);

    // QA Bloque AU (2026-05-06): validaciones explicitas de unicidad por
    // nombre corto, SWIFT y codigo ACH. Antes el UK de BD disparaba
    // DataIntegrityViolation que el GlobalExceptionHandler mapeaba a
    // "la empresa tiene registros asociados" (mensaje ambiguo).
    boolean existsByNameShortAndDeletedAtIsNull(String nameShort);

    boolean existsByNameShortAndIdNotAndDeletedAtIsNull(String nameShort, Long id);

    boolean existsBySwiftAndDeletedAtIsNull(String swift);

    boolean existsBySwiftAndIdNotAndDeletedAtIsNull(String swift, Long id);

    boolean existsByCodeAchAndDeletedAtIsNull(String codeAch);

    boolean existsByCodeAchAndIdNotAndDeletedAtIsNull(String codeAch, Long id);

    Optional<Bank> findByIdAndDeletedAtIsNull(Long id);

    List<Bank> findByCodeAndDeletedAtIsNull(String code);

    List<Bank> findByNameContainingIgnoreCaseAndDeletedAtIsNull(String name);
}