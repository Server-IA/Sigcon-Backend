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

    Optional<Bank> findByIdAndDeletedAtIsNull(Long id);

    List<Bank> findByCodeAndDeletedAtIsNull(String code);

    List<Bank> findByNameContainingIgnoreCaseAndDeletedAtIsNull(String name);
}