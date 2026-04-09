package com.sigcon.backend.banks.reconciliation.application;

import com.sigcon.backend.banks.reconciliation.domain.model.enums.ReconciliationSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cuadre de conciliación: extracto (movimientos importados) vs saldos declarados y vs libro (comprobantes).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankReconciliationSummaryDTO {

    private Long sessionId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private ReconciliationSessionStatus status;

    /** Saldo inicial según extracto (capturado en la sesión). */
    private BigDecimal statementOpeningBalance;
    /** Saldo final según extracto (capturado en la sesión). */
    private BigDecimal statementClosingBalance;

    /** Suma neta de movimientos bancarios en el periodo (por fecha de movimiento). */
    private BigDecimal movementsInPeriodNetSum;
    private long movementsInPeriodCount;

    /** Suma neta solo de movimientos vinculados a esta sesión. */
    private BigDecimal movementsLinkedToSessionNetSum;

    private long unmatchedMovementsInPeriodCount;
    /** Suma neta de movimientos del periodo aún sin emparejar con comprobante/cheque. */
    private BigDecimal unmatchedMovementsInPeriodNetSum;

    /**
     * Saldo calculado: saldo inicial extracto + movimientos del periodo.
     * Null si falta el saldo inicial del extracto.
     */
    private BigDecimal computedClosingFromExtractOpening;

    /**
     * Diferencia entre el saldo calculado y el saldo final del extracto (debería ser ~0).
     * Null si faltan datos.
     */
    private BigDecimal extractArithmeticDifference;

    /** True si la aritmética del extracto cuadra (tolerancia 1 céntimo). */
    private Boolean extractArithmeticOk;

    private BigDecimal bankAccountInitialBalance;
    /** Suma de importes de comprobantes con esta cuenta bancaria hasta la fecha fin del periodo. */
    private BigDecimal voucherMovementsUpToPeriodEndSum;

    /**
     * Aproximación de saldo en libros a la fecha fin del periodo: saldo inicial de la cuenta + comprobantes hasta esa fecha.
     */
    private BigDecimal bookBalanceAtPeriodEnd;

    /**
     * Saldo final extracto menos saldo libro a la fecha fin (debería tender a 0 si todo está registrado y emparejado).
     * Null si falta el saldo final del extracto.
     */
    private BigDecimal statementClosingVsBookDifference;

    /** True si extracto final y saldo libro coinciden (tolerancia 1 céntimo). */
    private Boolean statementClosingMatchesBook;
}
