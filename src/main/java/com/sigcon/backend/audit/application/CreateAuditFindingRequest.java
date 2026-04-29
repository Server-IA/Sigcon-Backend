package com.sigcon.backend.audit.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * HU-AU-08 E4: request para crear un hallazgo nuevo (estado inicial ABIERTO).
 */
@Data
@Schema(description = "Crear nuevo hallazgo de auditoria")
public class CreateAuditFindingRequest {

    @NotNull(message = "auditLogId es obligatorio")
    @Schema(description = "ID del log de auditoria al que se vincula", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long auditLogId;

    @NotBlank(message = "El titulo del hallazgo es obligatorio")
    @Size(max = 200, message = "Titulo demasiado largo (max 200 caracteres)")
    @Schema(description = "Titulo corto", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Size(max = 2000, message = "Descripcion demasiado larga (max 2000 caracteres)")
    @Schema(description = "Descripcion detallada del hallazgo")
    private String description;

    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL",
             message = "Severidad debe ser LOW, MEDIUM, HIGH o CRITICAL")
    @Schema(description = "Severidad", allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"})
    private String severity;

    @Size(max = 100)
    @Schema(description = "Email del revisor asignado (opcional al crear)")
    private String assignedTo;
}
