package com.sigcon.backend.parametrization.reports.application;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de solicitud para crear una plantilla de reporte.
 * La version se calcula automaticamente en el servicio (MAX(version)+1 por tipo).
 * Los campos validFrom/validTo/isDefault cubren la HU-PA-RF-39 (E1, E2, E3).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTemplateRequest {

    @NotNull(message = "El tipo de reporte es obligatorio")
    private Long reportTypeId;

    @Size(max = 500, message = "La descripcion no puede exceder 500 caracteres")
    private String description;

    @Size(max = 500, message = "La ruta del archivo no puede exceder 500 caracteres")
    private String filePath;

    /** HU-PA-RF-39 E1: fecha inicio vigencia (obligatoria). */
    @NotNull(message = "La fecha de vigencia inicial es obligatoria")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validFrom;

    /** HU-PA-RF-39 E1: fecha fin vigencia (opcional; NULL = indefinido). */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validTo;

    /** HU-PA-RF-39 E3: marca la plantilla como por defecto para el tipo de reporte. */
    @Builder.Default
    private Boolean isDefault = false;
}
