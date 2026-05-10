package com.sigcon.backend.banks.reconciliation.domain.repository;

import com.sigcon.backend.banks.reconciliation.domain.model.BankReconciliationSession;
import com.sigcon.backend.banks.reconciliation.domain.model.enums.ReconciliationSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankReconciliationSessionRepository extends JpaRepository<BankReconciliationSession, Long> {

    List<BankReconciliationSession> findByBankAccount_IdOrderByPeriodEndDesc(Long bankAccountId);

    boolean existsByBankAccount_IdAndStatus(Long bankAccountId, ReconciliationSessionStatus status);

    /** QA Bloque AU+ Bug 3 (2026-05-07): cuenta sesiones de conciliacion abiertas
     *  (status DRAFT) en todas las cuentas de un banco para bloquear inactivacion. */
    long countByBankAccount_Bank_IdAndStatus(Long bankId, ReconciliationSessionStatus status);
}
