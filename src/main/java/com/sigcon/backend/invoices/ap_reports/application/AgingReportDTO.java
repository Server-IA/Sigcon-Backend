package com.sigcon.backend.invoices.ap_reports.application;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para el reporte de antiguedad de saldos (aging report) del modulo AP.
 * Clasifica las facturas pendientes por rango de dias de vencimiento.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgingReportDTO {

    /** Total general de saldos pendientes. */
    private BigDecimal totalPending;

    /** Desglose por rangos de antiguedad. */
    private List<AgingBucketDTO> buckets;

    /** Detalle de facturas por rango. */
    private List<AgingInvoiceDTO> invoices;

    /**
     * Rango de antiguedad con su monto acumulado.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AgingBucketDTO {
        /** Etiqueta del rango (ej: "0-30 dias"). */
        private String range;
        /** Monto total en este rango. */
        private BigDecimal amount;
        /** Cantidad de facturas en este rango. */
        private int count;
    }

    /**
     * Detalle de una factura dentro del reporte de antiguedad.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AgingInvoiceDTO {
        /** ID de la factura. */
        private Long invoiceId;
        /** Numero de resolucion de la factura. */
        private String invoiceNumber;
        /** Nombre del proveedor. */
        private String supplierName;
        /** Saldo pendiente. */
        private BigDecimal balanceDue;
        /** Dias de vencimiento. */
        private long daysOverdue;
        /** Rango de antiguedad. */
        private String range;
    }
}
