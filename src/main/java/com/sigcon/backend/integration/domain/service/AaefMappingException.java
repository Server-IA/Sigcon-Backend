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

    private final String errorCode;
    private final boolean retryAllowed;

    public AaefMappingException(String errorCode, String message) {
        this(errorCode, message, true);
    }

    public AaefMappingException(String errorCode, String message, boolean retryAllowed) {
        super(message);
        this.errorCode = errorCode;
        this.retryAllowed = retryAllowed;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryAllowed() {
        return retryAllowed;
    }
}
