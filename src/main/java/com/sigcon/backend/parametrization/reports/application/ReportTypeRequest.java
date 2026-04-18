package com.sigcon.backend.parametrization.reports.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de solicitud para crear o actualizar un tipo de reporte.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTypeRequest {

    @NotBlank(message = "El nombre del tipo de reporte es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String name;

    @Size(max = 500, message = "La descripcion no puede exceder 500 caracteres")
    private String description;

    @Builder.Default
    private String status = "ACTIVE";
}
