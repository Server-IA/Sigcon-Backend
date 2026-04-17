package com.sigcon.backend.accounts_receivable.sales_invoices.domain.model;

/**
 * HU-AR-01A E6 / HU-AR-15: Estado de la factura electronica frente a DIAN.
 *
 * <ul>
 *   <li>{@code PENDING}: aun no se ha enviado el XML al PSE / DIAN.</li>
 *   <li>{@code SENT}: XML enviado, esperando respuesta DIAN.</li>
 *   <li>{@code ACCEPTED}: DIAN acepto el XML (factura electronica valida).</li>
 *   <li>{@code REJECTED}: DIAN rechazo el XML (errores estructurales o de negocio).</li>
 *   <li>{@code VOIDED}: factura anulada despues de aceptacion (requiere nota credito DIAN).</li>
 * </ul>
 */
public enum DianStatus {
    PENDING,
    SENT,
    ACCEPTED,
    REJECTED,
    VOIDED
}
