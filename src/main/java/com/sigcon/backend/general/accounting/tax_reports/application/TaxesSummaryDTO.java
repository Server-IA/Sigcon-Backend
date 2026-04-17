package com.sigcon.backend.general.accounting.tax_reports.application;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO del reporte consolidado anual de impuestos y retenciones (HU-CG-34).
 * <p>Agrupa IVA generado/descontable y retenciones practicadas/soportadas
 * por mes para todo el anio gravable.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxesSummaryDTO {

    private Integer year;

    /** IVA generado anual (ventas). */
    private BigDecimal totalIvaGenerado;

    /** IVA descontable anual (compras). */
    private BigDecimal totalIvaDescontable;

    /** Saldo IVA anual (generado - descontable). */
    private BigDecimal saldoIvaAnual;

    /** Retenciones practicadas a terceros en el anio. */
    private BigDecimal totalRetencionesPracticadas;

    /** Retenciones que nos practicaron los clientes (placeholder, por ahora 0). */
    private BigDecimal totalRetencionesSoportadas;

    /** Desglose mes a mes (12 filas). */
    private List<MonthlyTaxSummaryDTO> monthlySummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyTaxSummaryDTO {
        /** Numero del mes (1-12). */
        private Integer month;
        /** Etiqueta del mes (ej. "Enero"). */
        private String monthLabel;
        private BigDecimal ivaGenerado;
        private BigDecimal ivaDescontable;
        private BigDecimal saldoIva;
        private BigDecimal retencionesPracticadas;
        private BigDecimal retencionesSoportadas;
    }
}
