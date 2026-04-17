package com.sigcon.backend.banks.reconciliation.domain.repository;

import com.sigcon.backend.banks.reconciliation.domain.model.BankReconciliationSession;
import com.sigcon.backend.banks.reconciliation.domain.model.enums.ReconciliationSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankReconciliationSessionRepository extends JpaRepository<BankReconciliationSession, Long> {

    List<BankReconciliationSession> findByBankAccount_IdOrderByPeriodEndDesc(Long bankAccountId);

    boolean existsByBankAccount_IdAndStatus(Long bankAccountId, ReconciliationSessionStatus status);

}
