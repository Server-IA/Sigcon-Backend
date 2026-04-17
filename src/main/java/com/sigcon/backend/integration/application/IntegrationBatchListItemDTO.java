package com.sigcon.backend.integration.application;

import com.sigcon.backend.integration.domain.model.IntegrationBatch;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO resumido de un lote AAEF para el listado paginado del frontend
 * (HU-INT-RF-14 E1). Excluye el payload JSON (se descarga aparte via
 * endpoint /payload) para mantener la respuesta ligera.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resumen de un lote AAEF para listado (HU-INT-RF-14)")
public class IntegrationBatchListItemDTO {

    @Schema(description = "ID interno del lote", example = "1")
    private Long id;

    @Schema(description = "ExchangeId de AgroFusion", example = "AF-2026-04-00300")
    private String exchangeId;

    @Schema(description = "Version del estandar AAEF", example = "1.0")
    private String standardVersion;

    @Schema(description = "Sistema origen (Disriego/Sigma/AgroFusion)", example = "Disriego")
    private String sourceSystemId;

    @Schema(description = "Estado del procesamiento",
            allowableValues = {"RECEIVED", "PROCESSING", "PROCESSED", "PARTIAL",
                               "FAILED", "ACK_PENDING", "ACK_SENT", "ACK_FAILED"})
    private String status;

    @Schema(description = "Total de documentos en el lote", example = "5")
    private Integer totalDocuments;

    @Schema(description = "Fecha de recepcion")
    private LocalDateTime receivedAt;

    @Schema(description = "Fecha de procesamiento (null si aun no procesado)")
    private LocalDateTime processedAt;

    @Schema(description = "Cuenta de documentos con transfer_status=FAILED (para filtro 'solo fallidos')",
            example = "0")
    private Integer failedDocuments;

    public static IntegrationBatchListItemDTO from(IntegrationBatch b, int failedCount) {
        return IntegrationBatchListItemDTO.builder()
                .id(b.getId())
                .exchangeId(b.getExchangeId())
                .standardVersion(b.getStandardVersion())
                .sourceSystemId(b.getSourceSystemId())
                .status(b.getStatus() != null ? b.getStatus().name() : null)
                .totalDocuments(b.getTotalDocuments())
                .receivedAt(b.getReceivedAt())
                .processedAt(b.getProcessedAt())
                .failedDocuments(failedCount)
                .build();
    }
}
