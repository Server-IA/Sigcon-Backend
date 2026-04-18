package com.sigcon.backend.general.accounting.statements.application;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para el Estado de Cambios en el Patrimonio (HU-CG-18).
 *
 * Cuarto estado financiero obligatorio conforme a NIC 1. Muestra los
 * movimientos del patrimonio (clase 3 PUC colombiano) durante el periodo:
 * saldo inicial, aportes/retiros de capital, utilidad neta del ejercicio,
 * reservas, resultados acumulados, dividendos decretados y saldo final.
 *
 * Clasificacion por codigo PUC:
 * <ul>
 *   <li>31 - Capital Social (aportes)</li>
 *   <li>32 - Superavit de Capital</li>
 *   <li>33 - Reservas</li>
 *   <li>34 - Revalorizacion del Patrimonio</li>
 *   <li>36 - Resultados del Ejercicio (utilidad neta)</li>
 *   <li>37 - Resultados de Ejercicios Anteriores</li>
 *   <li>38 - Superavit por Valorizaciones</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EstadoPatrimonioDTO {

    /** Saldo del patrimonio al inicio del periodo. */
    private BigDecimal saldoInicial;

    /** Aportes del periodo (cuentas PUC 31 - Capital Social). */
    private BigDecimal aportes;

    /** Utilidad neta del ejercicio (cuentas PUC 36). */
    private BigDecimal utilidadNeta;

    /** Reservas constituidas (cuentas PUC 33). */
    private BigDecimal reservas;

    /** Resultados acumulados de ejercicios anteriores (cuentas PUC 37). */
    private BigDecimal resultadosAcumulados;

    /** Dividendos decretados durante el periodo (debitos a cuentas PUC 37/36). */
    private BigDecimal dividendosDecretados;

    /** Otros movimientos del patrimonio (PUC 32, 34, 38). */
    private BigDecimal otrosMovimientos;

    /** Saldo del patrimonio al final del periodo. */
    private BigDecimal saldoFinal;

    /** Detalle por cuenta de clase 3 (patrimonio). */
    private List<AccountMovementDTO> details;

    /**
     * Detalle del movimiento de una cuenta de patrimonio durante el periodo.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AccountMovementDTO {
        private String pucCode;
        private String accountName;
        private BigDecimal saldoInicial;
        private BigDecimal movimientosDebito;
        private BigDecimal movimientosCredito;
        private BigDecimal saldoFinal;
    }
}
