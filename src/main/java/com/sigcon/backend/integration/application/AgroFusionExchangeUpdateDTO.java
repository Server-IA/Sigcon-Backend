package com.sigcon.backend.integration.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HU-INT-RF-10: Payload del endpoint {@code POST /api/contabilidad/anulaciones}
 * usado por AgroFusion para notificar cambios diferenciales (Pull+Diff) sobre
 * documentos previamente contabilizados en SIGCON.
 *
 * <p>Campos:
 * <ul>
 *   <li>{@code originalExchangeId}: el exchangeId del lote AAEF donde se recibio
 *       originalmente el documento a anular o modificar. Si es changeType=NEW,
 *       representa el exchangeId nuevo del documento agregado.</li>
 *   <li>{@code changeType}: accion a ejecutar (CANCELLED | MODIFIED | NEW).</li>
 *   <li>{@code documentType}: tipo del documento afectado (INVOICE | TRANSACTION).</li>
 *   <li>{@code documentId}: identificador del documento en AgroFusion.</li>
 *   <li>{@code document}: JSON crudo del documento (mismo formato AAEF). Obligatorio
 *       para MODIFIED y NEW; opcional para CANCELLED.</li>
 *   <li>{@code reason}: texto libre con el motivo del cambio.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Notificacion diferencial de AgroFusion (Pull+Diff) - HU-INT-RF-10")
public class AgroFusionExchangeUpdateDTO {

    @NotBlank(message = "originalExchangeId es obligatorio")
    @JsonProperty("OriginalExchangeId")
    @Schema(description = "ExchangeId del lote original (o el nuevo si changeType=NEW)", example = "AF-2026-04-00300")
    private String originalExchangeId;

    @NotNull(message = "changeType es obligatorio")
    @JsonProperty("ChangeType")
    @Schema(description = "CANCELLED, MODIFIED o NEW", example = "CANCELLED")
    private ChangeType changeType;

    @JsonProperty("DocumentType")
    @Schema(description = "INVOICE o TRANSACTION", example = "INVOICE")
    private String documentType;

    @JsonProperty("DocumentId")
    @Schema(description = "DocumentId del documento afectado", example = "INV-T02-300")
    private String documentId;

    @JsonProperty("Document")
    @Schema(description = "JSON crudo del documento (obligatorio para MODIFIED y NEW)")
    private JsonNode document;

    @JsonProperty("Reason")
    @Schema(description = "Motivo textual del cambio", example = "Cancelacion por error de captura")
    private String reason;

    /** Tipos de cambio soportados por el flujo Pull+Diff. */
    public enum ChangeType {
        CANCELLED,
        MODIFIED,
        NEW
    }
}
