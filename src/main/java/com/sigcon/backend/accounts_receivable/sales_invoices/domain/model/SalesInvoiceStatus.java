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
    OVERDUE;

    /**
     * QA Bloque BN (2026-05-18): etiqueta en espanol para usar en UI, PDFs y
     * exports. Evita exponer el valor crudo del enum (ej. "ISSUED") al usuario
     * final. Si llega un valor desconocido (defensivo), devuelve el name() tal
     * cual.
     */
    public String toLabelEs() {
        switch (this) {
            case DRAFT: return "Borrador";
            case ISSUED: return "Emitida";
            case PARTIALLY_PAID: return "Pago Parcial";
            case PAID: return "Pagada";
            case VOIDED: return "Anulada";
            case SETTLED: return "Liquidada";
            case OVERDUE: return "Vencida";
            default: return this.name();
        }
    }

    /**
     * Helper estatico para mapear desde String (codigo del enum) a etiqueta es.
     * Util cuando solo se tiene el valor textual (ej. del DTO o de un join SQL).
     * Si {@code raw} es null o no corresponde a un valor del enum, devuelve el
     * mismo {@code raw} sin transformar (defensivo).
     */
    public static String labelOf(String raw) {
        if (raw == null) return null;
        try {
            return SalesInvoiceStatus.valueOf(raw).toLabelEs();
        } catch (IllegalArgumentException ex) {
            return raw;
        }
    }
}
