package com.sigcon.backend.general.accounting.books.application;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para una cuenta del Balance de Comprobacion.
 * Incluye saldo anterior, movimientos del periodo y saldo final
 * para verificar la cuadratura contable.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BalanceComprobacionDTO {

    /** Identificador de la cuenta contable. */
    private Long accountId;

    /** Codigo PUC de la cuenta. */
    private String pucCode;

    /** Nombre de la cuenta contable. */
    private String accountName;

    /** Saldo anterior - columna debito. */
    private BigDecimal saldoAnteriorDebit;

    /** Saldo anterior - columna credito. */
    private BigDecimal saldoAnteriorCredit;

    /** Movimientos del periodo - columna debito. */
    private BigDecimal movimientoDebit;

    /** Movimientos del periodo - columna credito. */
    private BigDecimal movimientoCredit;

    /** Saldo final - columna debito. */
    private BigDecimal saldoFinalDebit;

    /** Saldo final - columna credito. */
    private BigDecimal saldoFinalCredit;
}
