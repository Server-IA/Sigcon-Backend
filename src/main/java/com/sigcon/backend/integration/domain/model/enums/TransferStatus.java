package com.sigcon.backend.integration.domain.model.enums;

/**
 * Estado de procesamiento de un documento individual dentro de un lote AAEF.
 *
 * <p>Cada lote contiene multiples documentos (invoices, transactions). Cada
 * documento se rastrea en {@code integration_transfers} con uno de estos estados.
 *
 * <p>Transiciones:
 * <ul>
 *   <li>{@code PENDING} → {@code PROCESSED} (documento convertido a JE exitosamente)</li>
 *   <li>{@code PENDING} → {@code FAILED}    (validacion o mapeo fallo)</li>
 *   <li>{@code FAILED}  → {@code RETRYING}  (admin dispara reintento manual - HU-INT-RF-15)</li>
 *   <li>{@code RETRYING} → {@code PROCESSED} o {@code FAILED}</li>
 * </ul>
 */
public enum TransferStatus {

    /** Documento recibido dentro del lote, pendiente de procesar. */
    PENDING,

    /** Documento procesado exitosamente y con asiento contable generado. */
    PROCESSED,

    /** Documento fallido durante validacion o mapeo. Detalle en error_message. */
    FAILED,

    /** Documento en reintento manual tras fallo previo (HU-INT-RF-15). */
    RETRYING
}
