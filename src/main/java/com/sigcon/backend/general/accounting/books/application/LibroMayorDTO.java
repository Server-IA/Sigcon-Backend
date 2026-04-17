package com.sigcon.backend.general.accounting.books.application;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para una cuenta del Libro Mayor.
 * Agrupa los movimientos por cuenta contable con totales de debito/credito y saldo.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LibroMayorDTO {

    /** Identificador de la cuenta contable. */
    private Long accountId;

    /** Codigo PUC de la cuenta. */
    private String pucCode;

    /** Nombre de la cuenta contable. */
    private String accountName;

    /** Total de debitos en el periodo. */
    private BigDecimal totalDebit;

    /** Total de creditos en el periodo. */
    private BigDecimal totalCredit;

    /** Saldo neto (debito - credito). */
    private BigDecimal balance;
}
