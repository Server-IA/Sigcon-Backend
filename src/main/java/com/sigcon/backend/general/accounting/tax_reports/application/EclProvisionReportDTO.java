package com.sigcon.backend.general.accounting.tax_reports.application;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para el reporte de Provision ECL (HU-CG-31).
 * <p>Refleja la Perdida Crediticia Esperada (NIIF 9) sobre cuentas por
 * cobrar al cierre del anio, agrupada por buckets de mora y por cliente.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EclProvisionReportDTO {

    /** Anio al que corresponde el reporte (cierre al 31-dic). */
    private Integer year;

    /** Cartera total (suma de balanceDue de todas las facturas vigentes). */
    private BigDecimal totalCartera;

    /** Provision ECL total calculada aplicando los tramos por mora. */
    private BigDecimal totalProvision;

    /** Totales por bucket de mora (0-30, 31-60, 61-90, 91-180, >180 dias). */
    private List<EclBucketDTO> buckets;

    /** Detalle por cliente con su saldo y su ECL. */
    private List<EclCustomerDTO> details;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EclBucketDTO {
        /** Etiqueta del bucket (ej. "0-30 dias"). */
        private String label;
        /** Suma de saldos en el bucket. */
        private BigDecimal totalBalance;
        /** Tasa aplicada (0.01, 0.05, 0.20, 0.50, 1.00). */
        private BigDecimal eclRate;
        /** Monto de ECL del bucket. */
        private BigDecimal eclAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EclCustomerDTO {
        private Long thirdPartyId;
        private String nit;
        private String name;
        /** Saldo total vigente del cliente. */
        private BigDecimal totalBalance;
        /** ECL total del cliente sumando sus facturas. */
        private BigDecimal totalEcl;
    }
}
