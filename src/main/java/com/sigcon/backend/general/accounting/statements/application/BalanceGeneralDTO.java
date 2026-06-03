package com.sigcon.backend.general.accounting.statements.application;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para el Balance General (Estado de Situacion Financiera).
 * Estructura conforme a NIC 1 y PUC colombiano: Activos = Pasivos + Patrimonio.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BalanceGeneralDTO {

    /** Total de activos (clase 1 PUC). */
    private BigDecimal totalActivos;

    /** Total de pasivos (clase 2 PUC). */
    private BigDecimal totalPasivos;

    /**
     * Total de patrimonio (clase 3 PUC) YA AJUSTADO con el Resultado del Ejercicio.
     * HU-CG-09 E1: cuando no existe asiento de cierre, el resultado acumulado
     * (ingresos - gastos - costos) se incorpora al patrimonio para que la ecuacion
     * contable cuadre (NIC 1).
     */
    private BigDecimal totalPatrimonio;

    /**
     * HU-CG-09 E1: Resultado del Ejercicio acumulado (ingresos - gastos - costos)
     * incorporado al patrimonio. Positivo = utilidad, negativo = perdida.
     * Es 0 cuando ya se ejecuto el asiento de cierre (las clases 4/5/6/7 quedan en 0).
     */
    private BigDecimal resultadoEjercicio;

    /** Indica si la ecuacion contable esta balanceada: Activos = Pasivos + Patrimonio. */
    private Boolean isBalanced;

    /** Detalle por clase contable con desglose por cuenta. */
    private List<ClassDetailDTO> details;

    /**
     * Detalle de una clase contable dentro del balance.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ClassDetailDTO {
        private String className;
        private BigDecimal total;
        private List<AccountDetailDTO> accounts;
    }

    /**
     * Detalle de una cuenta dentro de una clase contable.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AccountDetailDTO {
        private Long accountId;
        private String pucCode;
        private String accountName;
        private BigDecimal balance;
    }
}
