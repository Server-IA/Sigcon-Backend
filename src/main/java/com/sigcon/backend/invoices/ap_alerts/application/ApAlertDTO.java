package com.sigcon.backend.invoices.ap_alerts.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AP-11: DTO que representa una alerta de factura de compra.
 *
 * <p>Se usa para exponer tanto facturas proximas a vencer como facturas
 * ya vencidas. El campo {@code daysUntilDue} es negativo para facturas
 * vencidas (interpretar como dias de atraso).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApAlertDTO {

    /** ID de la factura. */
    private Long invoiceId;

    /** Numero de resolucion/factura. */
    private String invoiceNumber;

    /** NIT del proveedor. */
    private String supplierNit;

    /** Razon social del proveedor. */
    private String supplierName;

    /** Fecha de la factura. */
    private LocalDate invoiceDate;

    /** Fecha de vencimiento calculada. */
    private LocalDate dueDate;

    /** Dias hasta el vencimiento (negativo = vencida). */
    private Long daysUntilDue;

    /** Saldo pendiente de pago. */
    private BigDecimal balanceDue;

    /** Estado actual de la factura (PENDING/PARTIALLY_PAID). */
    private String status;

    /** Nivel de alerta: INFO, WARNING, CRITICAL. */
    private String severity;
}
