package com.sigcon.backend.integration.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.OffsetDateTimeSerializer;
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
 * <p>QA Bloque AU (2026-05-14): formato alineado byte-a-byte con el modelo
 * que AgroFusion confirmo en reunion del 09/05/2026 (Alejandra Barros):
 *
 * <pre>{@code
 * {
 *   "exchangeId": "AF-2026-04-000051",
 *   "batchId": 101,
 *   "status": "PARTIAL",
 *   "processedAt": "2026-04-18T08:15:22Z",
 *   "processedDocuments": [
 *     {"documentId":"...", "documentType":"INVOICE", "status":"PROCESSED",
 *      "accountingEntryId":3001, "errorCode":null, "errorMessage":null}
 *   ],
 *   "failedDocuments": [
 *     {"documentId":"...", "documentType":"INVOICE", "status":"FAILED",
 *      "accountingEntryId":null, "errorCode":"PERIOD_CLOSED", "errorMessage":"..."}
 *   ]
 * }
 * }</pre>
 *
 * <p>Cambios respecto al DTO previo:
 * <ul>
 *   <li>{@code originalExchangeId} renombrado a {@code exchangeId}.</li>
 *   <li>Agregado {@code batchId} (Long).</li>
 *   <li>{@code accountingEntryId} tipado como {@code Long} (antes era String).</li>
 *   <li>{@code ProcessedDocument} incluye siempre {@code errorCode:null} y
 *       {@code errorMessage:null} (campos requeridos por el contrato).</li>
 *   <li>{@code FailedDocument} incluye siempre {@code documentType},
 *       {@code status:"FAILED"} y {@code accountingEntryId:null}.</li>
 *   <li>{@code retryAllowed} ya no se serializa al ACK (campo interno).</li>
 *   <li>{@code processedAt} con precision de segundos y formato ISO con offset.</li>
 * </ul>
 *
 * <p>Se envia via {@code POST} al endpoint registrado por AgroFusion
 * (parametro {@code AGROFUSION_ACK_CALLBACK_URL}).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({"exchangeId", "batchId", "status", "processedAt",
        "processedDocuments", "failedDocuments"})
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AaefAckDTO {

    @JsonProperty("exchangeId")
    private String exchangeId;

    @JsonProperty("batchId")
    private Long batchId;

    /** ACCEPTED | PARTIAL | REJECTED */
    @JsonProperty("status")
    private String status;

    /**
     * Fecha de procesamiento en formato ISO-8601 con offset UTC.
     * AgroFusion espera precision de segundos ({@code 2026-04-18T08:15:22Z}),
     * no nanosegundos. Por eso se trunca y se serializa con offset {@code Z}.
     */
    @JsonProperty("processedAt")
    @JsonSerialize(using = OffsetDateTimeSerializer.class)
    private OffsetDateTime processedAt;

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
    @JsonPropertyOrder({"documentId", "documentType", "status", "accountingEntryId",
            "errorCode", "errorMessage"})
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class ProcessedDocument {
        @JsonProperty("documentId")
        private String documentId;

        /** INVOICE | TRANSACTION */
        @JsonProperty("documentType")
        private String documentType;

        /** PROCESSED */
        @JsonProperty("status")
        private String status;

        /** Obligatorio si el documento se proceso exitosamente. Tipo Long. */
        @JsonProperty("accountingEntryId")
        private Long accountingEntryId;

        /** Siempre null en ProcessedDocument (campo requerido por contrato). */
        @JsonProperty("errorCode")
        private String errorCode;

        /** Siempre null en ProcessedDocument (campo requerido por contrato). */
        @JsonProperty("errorMessage")
        private String errorMessage;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonPropertyOrder({"documentId", "documentType", "status", "accountingEntryId",
            "errorCode", "errorMessage"})
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class FailedDocument {
        @JsonProperty("documentId")
        private String documentId;

        /** INVOICE | TRANSACTION (obligatorio aun en fallos para que AgroFusion sepa el tipo) */
        @JsonProperty("documentType")
        private String documentType;

        /** FAILED */
        @JsonProperty("status")
        private String status;

        /** Siempre null en FailedDocument (campo requerido por contrato). */
        @JsonProperty("accountingEntryId")
        private Long accountingEntryId;

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
    }
}
