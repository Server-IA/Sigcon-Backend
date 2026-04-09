package com.sigcon.backend.banks.reconciliation.domain.repository;

import com.sigcon.backend.banks.reconciliation.domain.model.BankReconciliationSession;
import com.sigcon.backend.banks.reconciliation.domain.model.enums.ReconciliationSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankReconciliationSessionRepository extends JpaRepository<BankReconciliationSession, Long> {

    List<BankReconciliationSession> findByBankAccount_IdOrderByPeriodEndDesc(Long bankAccountId);

    boolean existsByBankAccount_IdAndStatus(Long bankAccountId, ReconciliationSessionStatus status);

    Optional<BankReconciliationSession> findByIdAndCompany_Id(Long id, Long companyId);
}
