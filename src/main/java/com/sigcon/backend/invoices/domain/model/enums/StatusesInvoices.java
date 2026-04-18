package com.sigcon.backend.invoices.domain.model.enums;

/**
 * Estados posibles de una factura en el modulo Cuentas por Pagar.
 * <ul>
 *   <li>PENDING: Factura registrada, pendiente de pago.</li>
 *   <li>PAID: Factura totalmente pagada.</li>
 *   <li>PARTIALLY_PAID: Factura con abonos parciales.</li>
 *   <li>VOIDED: Factura anulada (no permite modificaciones).</li>
 *   <li>SETTLED: Factura liquidada/cerrada contablemente.</li>
 * </ul>
 */
public enum StatusesInvoices {
    PENDING,
    PAID,
    PARTIALLY_PAID,
    VOIDED,
    SETTLED
}
