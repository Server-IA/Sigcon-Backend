package com.sigcon.backend.parametrization.reports.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para plantillas de reporte.
 * Incluye datos del tipo de reporte asociado para evitar consultas adicionales.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTemplateDTO {

    private Long id;
    private Long reportTypeId;
    private String reportTypeName;
    private Integer version;
    private String filePath;
    private String description;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Boolean isDefault;
    private Boolean hasFile;
    private String status;
    private LocalDateTime createdAt;
}
