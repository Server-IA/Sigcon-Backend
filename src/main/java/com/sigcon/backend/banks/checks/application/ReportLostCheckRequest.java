package com.sigcon.backend.banks.checks.application;

import com.sigcon.backend.banks.checks.domain.model.enums.IncidentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReportLostCheckRequest {

    @NotNull(message = "La fecha de incidente es obligatoria")
    private LocalDate incidentDate;

    @NotNull(message = "El tipo de incidente es obligatorio")
    private IncidentType incidentType;

    @NotBlank(message = "El detalle del incidente es obligatorio")
    @Size(max = 1000, message = "El detalle del incidente no puede superar 1000 caracteres")
    private String incidentDetail;

    @NotBlank(message = "Las acciones tomadas son obligatorias")
    @Size(max = 500, message = "Las acciones tomadas no pueden superar 500 caracteres")
    private String incidentActions;
}
