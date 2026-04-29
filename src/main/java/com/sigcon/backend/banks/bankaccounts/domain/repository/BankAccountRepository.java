package com.sigcon.backend.banks.bankaccounts.domain.repository;

import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long>, JpaSpecificationExecutor<BankAccount> {

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndIdNotAndDeletedAtIsNull(String code, Long excludeId);

    boolean existsByBankIdAndAccountNumberAndDeletedAtIsNull(Long bankId, String accountNumber);

    boolean existsByBankIdAndAccountNumberAndIdNotAndDeletedAtIsNull(Long bankId, String accountNumber, Long excludeId);

    Optional<BankAccount> findByIdAndDeletedAtIsNull(Long id);

    /**
     * Verifica si existen cuentas bancarias activas asociadas a un banco.
     */
    boolean existsByBankIdAndDeletedAtIsNull(Long bankId);

    /** QA HU-008 E1: total de cuentas vigentes de un banco. */
    long countByBank_IdAndDeletedAtIsNull(Long bankId);

    /** QA HU-008 E1: cuentas en estado especifico de un banco. */
    long countByBank_IdAndStatusAndDeletedAtIsNull(
            Long bankId,
            com.sigcon.backend.banks.bankaccounts.domain.model.enums.BankAccountStatus status);

}
