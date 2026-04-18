package com.sigcon.backend.general.accounting.statements.application;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para el Estado de Flujos de Efectivo.
 * Estructura conforme a NIC 7: actividades de operacion, inversion y financiacion.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FlujoEfectivoDTO {

    /** Flujo neto de actividades de operacion. */
    private BigDecimal flujoOperativo;

    /** Flujo neto de actividades de inversion. */
    private BigDecimal flujoInversion;

    /** Flujo neto de actividades de financiacion. */
    private BigDecimal flujoFinanciacion;

    /** Flujo neto total del periodo. */
    private BigDecimal flujoNeto;

    /** Detalle de movimientos por tipo de actividad. */
    private List<ActivityDetailDTO> details;

    /**
     * Detalle de una actividad dentro del flujo de efectivo.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ActivityDetailDTO {
        private String activityType;
        private BigDecimal totalDebit;
        private BigDecimal totalCredit;
        private BigDecimal netFlow;
        private List<EntryDetailDTO> entries;
    }

    /**
     * Detalle de un asiento dentro de una actividad del flujo de efectivo.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EntryDetailDTO {
        private Long entryId;
        private Long entryNumber;
        private String description;
        private String sourceModule;
        private BigDecimal totalDebit;
        private BigDecimal totalCredit;
    }
}
