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

    /**
     * QA-BLOQUE-AP (2026-04-29): empareja con JournalEntry (no Voucher).
     * Devuelve la coleccion: un mismo JE puede emparejar varios FMs (1 JE
     * con multiples lineas que afectan la misma cuenta), pero por simplicidad
     * el flujo de emparejamiento es 1:1 (UI valida).
     */
    Optional<FinancialMovement> findByMatchedJournalEntryId(Long matchedJournalEntryId);

    /** HU-018 E4 (Bloque AO): suma total de movimientos de la cuenta para validar fondos al emitir cheque. */
    @Query("SELECT COALESCE(SUM(fm.amount), 0) FROM FinancialMovement fm WHERE fm.bankAccount.id = :bankAccountId")
    BigDecimal sumAmountByBankAccountId(@Param("bankAccountId") Long bankAccountId);

    /** HU-AP-04 E3 / HU-AP-05 E3 / HU-AP-07 E3 (Bloque AR): suma total de movimientos de una caja. */
    @Query("SELECT COALESCE(SUM(fm.amount), 0) FROM FinancialMovement fm WHERE fm.cash.id = :cashId")
    BigDecimal sumAmountByCashId(@Param("cashId") Long cashId);

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
