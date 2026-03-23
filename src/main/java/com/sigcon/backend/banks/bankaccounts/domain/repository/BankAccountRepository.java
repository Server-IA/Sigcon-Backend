package com.sigcon.backend.banks.bankaccounts.domain.repository;

import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long>, JpaSpecificationExecutor<BankAccount> {

    boolean existsByCompanyIdAndCodeAndDeletedAtIsNull(Long companyId, String code);

    boolean existsByCompanyIdAndCodeAndIdNotAndDeletedAtIsNull(Long companyId, String code, Long excludeId);

    boolean existsByBankIdAndAccountNumberAndDeletedAtIsNull(Long bankId, String accountNumber);

    boolean existsByBankIdAndAccountNumberAndIdNotAndDeletedAtIsNull(Long bankId, String accountNumber, Long excludeId);

    Optional<BankAccount> findByIdAndDeletedAtIsNull(Long id);
}
