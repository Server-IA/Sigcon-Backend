package com.sigcon.backend.integration.application;

import com.sigcon.backend.integration.domain.model.IntegrationTransfer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/** DTO del transfer de un documento dentro de un lote (HU-INT-RF-14 E2, HU-INT-RF-15). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Transfer de un documento del lote AAEF")
public class IntegrationTransferDTO {

    @Schema(description = "ID interno del transfer", example = "1")
    private Long id;

    @Schema(description = "DocumentId externo (AgroFusion)", example = "INV-DISRIEGO-T02-000200")
    private String documentId;

    @Schema(description = "Tipo de documento",
            allowableValues = {"INVOICE", "TRANSACTION"})
    private String documentType;

    @Schema(description = "Estado del transfer",
            allowableValues = {"PENDING", "PROCESSED", "FAILED", "RETRYING"})
    private String transferStatus;

    @Schema(description = "ID del asiento contable generado (null si fallo)", example = "42")
    private Long accountingEntryId;

    @Schema(description = "Codigo de error si fallo", example = "AMOUNT_MISMATCH")
    private String errorCode;

    @Schema(description = "Mensaje de error detallado")
    private String errorMessage;

    @Schema(description = "True si el error permite reintento (HU-INT-RF-15 E2)", example = "true")
    private Boolean retryAllowed;

    @Schema(description = "Numero de reintentos ejecutados", example = "0")
    private Integer retryCount;

    @Schema(description = "Fecha de procesamiento")
    private LocalDateTime processedAt;

    public static IntegrationTransferDTO from(IntegrationTransfer t) {
        return IntegrationTransferDTO.builder()
                .id(t.getId())
                .documentId(t.getDocumentId())
                .documentType(t.getDocumentType() != null ? t.getDocumentType().name() : null)
                .transferStatus(t.getTransferStatus() != null ? t.getTransferStatus().name() : null)
                .accountingEntryId(t.getAccountingEntryId())
                .errorCode(t.getErrorCode())
                .errorMessage(t.getErrorMessage())
                .retryAllowed(t.getRetryAllowed())
                .retryCount(t.getRetryCount())
                .processedAt(t.getProcessedAt())
                .build();
    }
}
