package com.sigcon.backend.general.accounting.tax_reports.application;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO del reporte de diferencias en cambio (HU-CG-33).
 * <p>Reporta la revaluacion de partidas monetarias en moneda extranjera
 * al cierre del periodo (NIC 21). Incluye facturas AR y AP con saldo
 * pendiente en moneda distinta a COP.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeDifferenceReportDTO {

    private Integer year;
    private Integer month;

    /** Suma de diferencias positivas (ganancias en cambio). */
    private BigDecimal totalGanancia;

    /** Suma de diferencias negativas (perdidas en cambio). */
    private BigDecimal totalPerdida;

    /** Diferencia neta: ganancia - perdida. */
    private BigDecimal diferenciaNeta;

    /** Detalle por factura. */
    private List<DifferenceItemDTO> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DifferenceItemDTO {
        private Long invoiceId;
        private String invoiceNumber;
        /** "FV" factura venta (AR) o "FC" factura compra (AP). */
        private String documentType;
        /** Codigo ISO de la moneda (ej. USD, EUR). */
        private String currency;
        /** Saldo pendiente en moneda extranjera. */
        private BigDecimal amountForeign;
        /** Tasa de cambio original con la que se contabilizo. */
        private BigDecimal originalRate;
        /** Tasa de cambio vigente al cierre del periodo. */
        private BigDecimal currentRate;
        /** Monto de la diferencia en COP (puede ser positivo o negativo). */
        private BigDecimal differenceAmount;
        /** "GANANCIA" si favorece al contribuyente, "PERDIDA" si no. */
        private String type;
    }
}
