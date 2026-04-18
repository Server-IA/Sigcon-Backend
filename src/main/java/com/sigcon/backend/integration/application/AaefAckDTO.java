package com.sigcon.backend.integration.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AAEF v1.0 - Estructura del ACK que SIGCON envia al callback de AgroFusion
 * tras procesar un lote (RF-INT-12 R17 + seccion "Contrato del ACK de Respuesta").
 *
 * <p>Se envia via {@code POST} al endpoint registrado por AgroFusion
 * (parametro {@code AGROFUSION_ACK_CALLBACK_URL}).
 *
 * <p>El ACK incluye:
 * <ul>
 *   <li>{@code originalExchangeId}: referencia al lote original.</li>
 *   <li>{@code status}: ACCEPTED | PARTIAL | REJECTED segun el resultado.</li>
 *   <li>{@code processedDocuments}: lista con accountingEntryId por documento OK.</li>
 *   <li>{@code failedDocuments}: lista con errorCode y errorMessage por documento fallido.</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AaefAckDTO {

    @JsonProperty("originalExchangeId")
    private String originalExchangeId;

    @JsonProperty("processedAt")
    private OffsetDateTime processedAt;

    /** ACCEPTED | PARTIAL | REJECTED */
    @JsonProperty("status")
    private String status;

    @JsonProperty("processedDocuments")
    @Builder.Default
    private List<ProcessedDocument> processedDocuments = new ArrayList<>();

    @JsonProperty("failedDocuments")
    @Builder.Default
    private List<FailedDocument> failedDocuments = new ArrayList<>();

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProcessedDocument {
        @JsonProperty("documentId")
        private String documentId;

        /** INVOICE | TRANSACTION */
        @JsonProperty("documentType")
        private String documentType;

        /** Obligatorio si el documento se proceso exitosamente. */
        @JsonProperty("accountingEntryId")
        private String accountingEntryId;

        /** PROCESSED */
        @JsonProperty("status")
        private String status;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FailedDocument {
        @JsonProperty("documentId")
        private String documentId;

        /**
         * Codigo de error estandarizado (RF-INT-12):
         * INVALID_STATUS, AMOUNT_MISMATCH, MISSING_LINE_TYPE, MISSING_INVOICE_REF,
         * MISSING_ADJUSTMENT_REASON, PERIOD_CLOSED, UNKNOWN_THIRD_PARTY,
         * ORIGINAL_NOT_FOUND, MAPPING_ERROR.
         */
        @JsonProperty("errorCode")
        private String errorCode;

        @JsonProperty("errorMessage")
        private String errorMessage;

        @JsonProperty("retryAllowed")
        private Boolean retryAllowed;
    }
}
