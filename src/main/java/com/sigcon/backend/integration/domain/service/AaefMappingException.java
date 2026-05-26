package com.sigcon.backend.integration.domain.service;

/**
 * Excepcion especifica lanzada por los mappers AAEF cuando un documento no puede
 * convertirse a una entidad SIGCON (por validaciones de negocio, falta de datos,
 * tercero inexistente, etc.).
 *
 * <p>El {@code errorCode} se propaga al {@code failedDocuments} del ACK enviado
 * a AgroFusion (HU-INT-RF-07). Ver RF-INT-12 para los codigos estandar.
 */
public class AaefMappingException extends RuntimeException {

    /** Codigos estandarizados del contrato AAEF (RF-INT-12). */
    public static final String INVALID_STATUS = "INVALID_STATUS";
    public static final String AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
    public static final String MISSING_LINE_TYPE = "MISSING_LINE_TYPE";
    public static final String MISSING_INVOICE_REF = "MISSING_INVOICE_REF";
    public static final String MISSING_ADJUSTMENT_REASON = "MISSING_ADJUSTMENT_REASON";
    public static final String PERIOD_CLOSED = "PERIOD_CLOSED";
    public static final String UNKNOWN_THIRD_PARTY = "UNKNOWN_THIRD_PARTY";
    public static final String ORIGINAL_NOT_FOUND = "ORIGINAL_NOT_FOUND";
    public static final String MAPPING_ERROR = "MAPPING_ERROR";
    public static final String UNSUPPORTED_TYPE = "UNSUPPORTED_TYPE";

    /** AgroFusion feedback v1.1 (2026-04-28). */
    public static final String INVALID_TYPE_CODE = "INVALID_TYPE_CODE";
    public static final String INVALID_ACCOUNTING_ACCOUNT = "INVALID_ACCOUNTING_ACCOUNT";
    public static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
    public static final String MISSING_ORIGINAL_REF = "MISSING_ORIGINAL_REF";

    /**
     * QA Bloque BJ (HU-INT-RF-03 E4, 2026-05-18): el mismo {@code DocumentId}
     * aparece varias veces dentro del mismo lote. Solo se procesa la PRIMERA
     * ocurrencia; las siguientes son rechazadas con este codigo (no retryable -
     * AgroFusion debe corregir el lote y reenviarlo con nuevo exchangeId).
     */
    public static final String DUPLICATE_DOCUMENT_ID = "DUPLICATE_DOCUMENT_ID";

    /**
     * QA Integracion (2026-05-26): coherencia estado factura vs transacciones.
     * Una factura llega con Status=PAID o PARTIAL pero el lote NO trae la
     * transaccion PAY que respalde ese estado (PAID exige PAY del 100%, PARTIAL
     * exige PAY parcial). No recuperable desde SIGCON: AgroFusion debe reenviar
     * el lote incluyendo la transaccion PAY correspondiente.
     */
    public static final String MISSING_PAYMENT = "MISSING_PAYMENT";

    private final String errorCode;
    private final boolean retryAllowed;

    /**
     * QA Bloque BK (HU-INT-RF-14 E2, 2026-05-18): set de errorCodes NO recuperables
     * desde SIGCON. Estos errores requieren correccion en origen (AgroFusion debe
     * reenviar el documento corregido). El boton "Reintentar" en la UI queda
     * deshabilitado y se muestra el mensaje "Este error no permite reintento.
     * Solicite nuevo envio a AgroFusion".
     *
     * <p>Errores que SI permiten reintento (retryAllowed=true por default):
     * <ul>
     *   <li>{@code PERIOD_CLOSED}: el contador puede reabrir el periodo y luego reintentar.</li>
     *   <li>{@code MAPPING_ERROR}: error interno SIGCON; tras fix de codigo el reintento procede.</li>
     *   <li>ACK timeout / IO: red transitoria.</li>
     * </ul>
     */
    private static final java.util.Set<String> NON_RECOVERABLE_ERROR_CODES = java.util.Set.of(
            AMOUNT_MISMATCH,
            INVALID_STATUS,
            MISSING_LINE_TYPE,
            MISSING_INVOICE_REF,
            MISSING_ADJUSTMENT_REASON,
            UNKNOWN_THIRD_PARTY,
            ORIGINAL_NOT_FOUND,
            UNSUPPORTED_TYPE,
            INVALID_TYPE_CODE,
            INVALID_ACCOUNTING_ACCOUNT,
            ACCOUNT_NOT_FOUND,
            MISSING_ORIGINAL_REF,
            DUPLICATE_DOCUMENT_ID,
            MISSING_PAYMENT
    );

    /**
     * QA Bloque BK (HU-INT-RF-14 E2, 2026-05-18): constructor por defecto que
     * resuelve {@code retryAllowed} automaticamente segun la naturaleza del
     * errorCode. Antes este constructor forzaba {@code retryAllowed=true}
     * incluso para errores no recuperables como AMOUNT_MISMATCH, lo que
     * permitia al usuario reintentar inutilmente desde la UI.
     */
    public AaefMappingException(String errorCode, String message) {
        this(errorCode, message, !NON_RECOVERABLE_ERROR_CODES.contains(errorCode));
    }

    public AaefMappingException(String errorCode, String message, boolean retryAllowed) {
        super(message);
        this.errorCode = errorCode;
        this.retryAllowed = retryAllowed;
    }

    /** Helper publico para que otros services (ej. AaefBatchProcessor) consulten si un errorCode es recuperable. */
    public static boolean isRetryable(String errorCode) {
        return errorCode != null && !NON_RECOVERABLE_ERROR_CODES.contains(errorCode);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryAllowed() {
        return retryAllowed;
    }
}
