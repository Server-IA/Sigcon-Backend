package com.sigcon.backend.accounts_receivable.sales_invoices.domain.model;

/**
 * Estados posibles de una factura de venta (FV) del modulo Cuentas por Cobrar.
 * DRAFT: borrador sin emitir
 * ISSUED: emitida, pendiente de pago
 * PARTIALLY_PAID: con abonos parciales
 * PAID: totalmente pagada
 * VOIDED: anulada
 * SETTLED: liquidada (saldo cero)
 * OVERDUE: vencida (fecha vencimiento superada con saldo pendiente)
 */
public enum SalesInvoiceStatus {
    DRAFT,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    VOIDED,
    SETTLED,
    OVERDUE
}
