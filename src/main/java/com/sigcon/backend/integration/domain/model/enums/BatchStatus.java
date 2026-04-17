package com.sigcon.backend.integration.domain.model.enums;

/**
 * Estados del ciclo de vida de un lote AAEF (IntegrationBatch).
 *
 * <p>Transiciones validas:
 * <ul>
 *   <li>{@code RECEIVED} → {@code PROCESSING}  (al iniciar procesamiento async)</li>
 *   <li>{@code PROCESSING} → {@code PROCESSED} (todos los documentos OK)</li>
 *   <li>{@code PROCESSING} → {@code PARTIAL}   (algunos documentos OK, otros fallidos)</li>
 *   <li>{@code PROCESSING} → {@code FAILED}    (todos los documentos fallaron)</li>
 *   <li>{@code PROCESSED|PARTIAL|FAILED} → {@code ACK_PENDING} → {@code ACK_SENT}</li>
 *   <li>{@code ACK_PENDING} → {@code ACK_FAILED} (tras 3 reintentos fallidos)</li>
 * </ul>
 *
 * <p>HUs asociadas: HU-INT-RF-01 (recepcion), HU-INT-RF-07 (ACK), HU-INT-RF-13 (retry).
 */
public enum BatchStatus {

    /** Lote recibido, validado estructuralmente y persistido. Pendiente de procesar. */
    RECEIVED,

    /** Lote en procesamiento asincrono (mapeo + generacion de asientos). */
    PROCESSING,

    /** Todos los documentos del lote fueron procesados exitosamente. */
    PROCESSED,

    /** Procesamiento parcial: algunos documentos OK, otros fallidos. */
    PARTIAL,

    /** Todos los documentos del lote fallaron durante el procesamiento. */
    FAILED,

    /** Procesamiento terminado, ACK aun no enviado al callback de AgroFusion. */
    ACK_PENDING,

    /** ACK enviado exitosamente a AgroFusion (HTTP 200 recibido). */
    ACK_SENT,

    /** ACK no pudo entregarse tras 3 reintentos. Requiere intervencion manual. */
    ACK_FAILED
}
