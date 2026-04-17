package com.sigcon.backend.parametrization.reports.application;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para tipos de reporte.
 * Contiene la informacion que se expone al cliente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTypeDTO {

    private Long id;
    private String name;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
