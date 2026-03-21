package com.sigcon.backend.bank.banks.domain.repository;

import com.sigcon.backend.bank.banks.domain.model.Bank;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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