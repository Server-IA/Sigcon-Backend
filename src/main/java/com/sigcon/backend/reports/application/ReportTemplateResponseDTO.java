package com.sigcon.backend.reports.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Metadatos del informe PDF generado")
public class ReportTemplateResponseDTO {

    @Schema(description = "Nombre del informe generado", example = "Plantilla Base SIGCON")
    private String reportName;

    @Schema(description = "Fecha y hora en que se generó el informe", example = "10/03/2026 23:30:00")
    private String generatedAt;

    @Schema(description = "Usuario que solicitó la generación del informe", example = "admin@sigcon.com")
    private String generatedBy;

    @Schema(description = "Tamaño del PDF generado en bytes", example = "42816")
    private long sizeBytes;
}
