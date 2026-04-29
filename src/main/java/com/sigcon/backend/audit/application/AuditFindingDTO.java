package com.sigcon.backend.audit.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 * HU-AU-08 E4: DTO de hallazgo de auditoria.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Hallazgo de auditoria con flujo ABIERTO/EN_REVISION/CERRADO")
public class AuditFindingDTO {
    @Schema(description = "ID interno", example = "1")
    private Long id;

    @Schema(description = "ID del log de auditoria vinculado", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long auditLogId;

    @Schema(description = "Titulo corto del hallazgo", example = "Eliminacion sospechosa de tercero")
    private String title;

    @Schema(description = "Descripcion del hallazgo", example = "El tercero eliminado tenia 5 facturas activas...")
    private String description;

    @Schema(description = "Estado del flujo", allowableValues = {"ABIERTO", "EN_REVISION", "CERRADO"}, example = "ABIERTO")
    private String status;

    @Schema(description = "Severidad", allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}, example = "HIGH")
    private String severity;

    @Schema(description = "Email del usuario que abrio")
    private String openedBy;

    @Schema(description = "Email del revisor asignado")
    private String assignedTo;

    @Schema(description = "Email de quien cerro")
    private String closedBy;

    @Schema(description = "Conclusion/resolucion al cerrar")
    private String resolution;

    private LocalDateTime openedAt;
    private LocalDateTime reviewStartedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
