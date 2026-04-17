package com.sigcon.backend.general.accounting.books.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para una linea del Auxiliar por Cuenta.
 * Muestra el detalle de cada movimiento de una cuenta especifica
 * con saldo acumulado progresivo.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuxiliarCuentaDTO {

    /** Fecha del asiento contable. */
    private LocalDate date;

    /** Numero del asiento contable. */
    private Long entryNumber;

    /** Descripcion o glosa de la linea. */
    private String description;

    /** Monto al debito. */
    private BigDecimal debit;

    /** Monto al credito. */
    private BigDecimal credit;

    /** Saldo acumulado hasta este movimiento. */
    private BigDecimal runningBalance;
}
