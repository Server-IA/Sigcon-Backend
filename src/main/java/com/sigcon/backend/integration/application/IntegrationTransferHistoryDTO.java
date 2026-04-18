package com.sigcon.backend.integration.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * HU-INT-RF-15 E4: representa un intento del historial de un transfer.
 * Se devuelve al frontend en el endpoint
 * {@code GET /api/contabilidad/transferencias/{id}/history}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Entrada del historial de un transfer (intento de procesamiento).")
public class IntegrationTransferHistoryDTO {

    @Schema(description = "ID del registro de historial", example = "42")
    private Long id;

    @Schema(description = "ID del transfer al que pertenece", example = "17")
    private Long transferId;

    @Schema(description = "Numero de intento. 0=inicial, 1+=retries", example = "1")
    private Integer attemptNumber;

    @Schema(description = "Resultado: SUCCESS / FAILED / RETRYING / SKIPPED", example = "SUCCESS")
    private String resultStatus;

    @Schema(description = "Codigo de error si fallo", example = "AMOUNT_MISMATCH")
    private String errorCode;

    @Schema(description = "Mensaje de error si fallo", example = "Subtotal no coincide con la suma de lineas")
    private String errorMessage;

    @Schema(description = "ID del JE generado si fue exitoso", example = "1234")
    private Long accountingEntryId;

    @Schema(description = "SYSTEM o MANUAL", example = "MANUAL")
    private String triggerSource;

    @Schema(description = "Quien gatillo el intento", example = "superadmin")
    private String triggeredBy;

    @Schema(description = "Nota del usuario al reintentar", example = "Reintento tras corregir tasa de cambio")
    private String userNote;

    @Schema(description = "ID del nuevo batch sintetico generado por el retry", example = "55")
    private Long newBatchId;

    @Schema(description = "Cuando ocurrio el intento", example = "2026-04-16T10:35:42")
    private LocalDateTime occurredAt;
}
