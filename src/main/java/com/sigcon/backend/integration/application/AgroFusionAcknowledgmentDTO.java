package com.sigcon.backend.integration.application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Spec AAEF Bloque W (Pull+Diff ACK): envelope PascalCase exigido por la
 * especificacion para los ACK de actualizaciones via
 * {@code AgroFusionExchangeUpdate}. Es DISTINTO al {@link AaefAckDTO}
 * camelCase que se usa para los lotes iniciales.
 *
 * <p>Estructura emitida en el body del POST al callback:
 * <pre>
 * {
 *   "AgroFusionAcknowledgment": {
 *     "version":            "1.0",
 *     "OriginalExchangeId": "<ExchangeId del update, no del lote padre>",
 *     "ProcessedAt":        "<timestamp ISO-8601>",
 *     "Status":             "ACCEPTED | PARTIAL | REJECTED",
 *     "ProcessedDocuments": [
 *       {
 *         "DocumentId":        "<id>",
 *         "DocumentType":      "INVOICE | TRANSACTION",
 *         "AccountingEntryId": "<id del asiento nuevo o de reversa>",
 *         "Status":            "PROCESSED | NO_CHANGE"
 *       }
 *     ],
 *     "FailedDocuments": []
 *   }
 * }
 * </pre>
 *
 * <p>Codigos de error aplicables (errorCode): INVALID_STATUS, AMOUNT_MISMATCH,
 * MISSING_LINE_TYPE, MISSING_INVOICE_REF, MISSING_ADJUSTMENT_REASON,
 * UNKNOWN_THIRD_PARTY, PERIOD_CLOSED, ORIGINAL_NOT_FOUND,
 * UNSUPPORTED_CHANGE_TYPE, MISSING_CHANGE_TYPE, MAPPING_ERROR, INTERNAL_ERROR.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgroFusionAcknowledgmentDTO {

    @JsonProperty("AgroFusionAcknowledgment")
    private Inner agroFusionAcknowledgment;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Inner {

        @Builder.Default
        @JsonProperty("version")
        private String version = "1.0";

        @JsonProperty("OriginalExchangeId")
        private String originalExchangeId;

        @JsonProperty("ProcessedAt")
        private LocalDateTime processedAt;

        /** ACCEPTED | PARTIAL | REJECTED */
        @JsonProperty("Status")
        private String status;

        @Builder.Default
        @JsonProperty("ProcessedDocuments")
        private List<ProcessedDocument> processedDocuments = new ArrayList<>();

        @Builder.Default
        @JsonProperty("FailedDocuments")
        private List<FailedDocument> failedDocuments = new ArrayList<>();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProcessedDocument {
        @JsonProperty("DocumentId")
        private String documentId;

        /** INVOICE | TRANSACTION */
        @JsonProperty("DocumentType")
        private String documentType;

        /** ID del asiento nuevo o de reversa generado por SIGCON. */
        @JsonProperty("AccountingEntryId")
        private String accountingEntryId;

        /** PROCESSED | NO_CHANGE */
        @JsonProperty("Status")
        private String status;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FailedDocument {
        @JsonProperty("DocumentId")
        private String documentId;

        @JsonProperty("ErrorCode")
        private String errorCode;

        @JsonProperty("ErrorMessage")
        private String errorMessage;

        @Builder.Default
        @JsonProperty("RetryAllowed")
        private Boolean retryAllowed = false;
    }
}
