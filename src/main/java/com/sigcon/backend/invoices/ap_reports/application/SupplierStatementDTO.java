package com.sigcon.backend.invoices.ap_reports.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para el estado de cuenta de un proveedor.
 * Incluye resumen y detalle de facturas, pagos y notas.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SupplierStatementDTO {

    /** ID del proveedor (tercero). */
    private Long thirdPartyId;

    /** Nombre o razon social del proveedor. */
    private String supplierName;

    /** NIT del proveedor. */
    private String supplierNit;

    /** Total facturado al proveedor. */
    private BigDecimal totalInvoiced;

    /** Total pagado al proveedor. */
    private BigDecimal totalPaid;

    /** Saldo pendiente total. */
    private BigDecimal totalBalance;

    /** Detalle de movimientos del proveedor. */
    private List<StatementLineDTO> lines;

    /**
     * Linea de detalle del estado de cuenta.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StatementLineDTO {
        /** Tipo de movimiento: FACTURA, PAGO, NOTA_CREDITO, NOTA_DEBITO. */
        private String type;
        /** Numero del documento. */
        private String documentNumber;
        /** Fecha del movimiento. */
        private LocalDate date;
        /** Monto del movimiento. */
        private BigDecimal amount;
        /** Saldo pendiente (solo para facturas). */
        private BigDecimal balance;
    }
}
