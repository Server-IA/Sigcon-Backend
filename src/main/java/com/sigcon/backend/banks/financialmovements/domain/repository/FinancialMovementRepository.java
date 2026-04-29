package com.sigcon.backend.banks.financialmovements.domain.repository;

import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinancialMovementRepository extends JpaRepository<FinancialMovement, Long>, JpaSpecificationExecutor<FinancialMovement> {

    @Query("SELECT fm FROM FinancialMovement fm WHERE fm.bankAccount.id = :bankAccountId "
            + "AND fm.matchedCheckId IS NULL AND fm.matchedVoucherId IS NULL "
            + "ORDER BY fm.movementDate DESC, fm.id DESC")
    List<FinancialMovement> findUnmatchedByBankAccountId(@Param("bankAccountId") Long bankAccountId);

    @Query("SELECT fm FROM FinancialMovement fm WHERE fm.bankAccount.id = :bankAccountId "
            + "ORDER BY fm.movementDate DESC, fm.id DESC")
    List<FinancialMovement> findAllByBankAccountIdOrdered(@Param("bankAccountId") Long bankAccountId);

    boolean existsByCash_Id(Long cashId);

    long countByCash_Id(Long cashId);

    /**
     * QA HU-003 E1: cuenta movimientos de una cuenta bancaria.
     * Usado para bloquear eliminacion de cuenta con movimientos asociados.
     * (FinancialMovement no tiene soft delete, los movimientos no se borran).
     */
    long countByBankAccount_Id(Long bankAccountId);

    boolean existsByMatchedVoucherId(Long matchedVoucherId);

    @Query("SELECT fm FROM FinancialMovement fm WHERE fm.id = :id AND fm.bankAccount.id = :bankAccountId "
            + "AND fm.matchedCheckId IS NULL AND fm.matchedVoucherId IS NULL")
    Optional<FinancialMovement> findForCheckReconcile(
            @Param("id") Long id,
            @Param("bankAccountId") Long bankAccountId);

    Optional<FinancialMovement> findByIdAndBankAccount_Id(Long id, Long bankAccountId);

    Optional<FinancialMovement> findByMatchedVoucherId(Long matchedVoucherId);

    @Query("SELECT COALESCE(SUM(fm.amount), 0) FROM FinancialMovement fm WHERE fm.bankAccount.id = :bankAccountId "
            + "AND fm.movementDate >= :from AND fm.movementDate <= :to")
    BigDecimal sumAmountByBankAccountAndPeriod(
            @Param("bankAccountId") Long bankAccountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(fm.amount), 0) FROM FinancialMovement fm WHERE fm.reconciliationSession.id = :sessionId")
    BigDecimal sumAmountByReconciliationSessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT COALESCE(SUM(fm.amount), 0) FROM FinancialMovement fm WHERE fm.bankAccount.id = :bankAccountId "
            + "AND fm.movementDate >= :from AND fm.movementDate <= :to "
            + "AND fm.matchedVoucherId IS NULL AND fm.matchedCheckId IS NULL")
    BigDecimal sumUnmatchedAmountByBankAccountAndPeriod(
            @Param("bankAccountId") Long bankAccountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT COUNT(fm) FROM FinancialMovement fm WHERE fm.bankAccount.id = :bankAccountId "
            + "AND fm.movementDate >= :from AND fm.movementDate <= :to "
            + "AND fm.matchedVoucherId IS NULL AND fm.matchedCheckId IS NULL")
    long countUnmatchedByBankAccountAndPeriod(
            @Param("bankAccountId") Long bankAccountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT COUNT(fm) FROM FinancialMovement fm WHERE fm.bankAccount.id = :bankAccountId "
            + "AND fm.movementDate >= :from AND fm.movementDate <= :to")
    long countByBankAccountAndPeriod(
            @Param("bankAccountId") Long bankAccountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
