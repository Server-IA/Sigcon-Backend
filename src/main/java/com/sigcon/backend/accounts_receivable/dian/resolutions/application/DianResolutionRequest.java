package com.sigcon.backend.accounts_receivable.dian.resolutions.application;

import java.time.LocalDate;

import com.sigcon.backend.accounts_receivable.dian.resolutions.domain.model.DianResolutionStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request para crear o actualizar una resolucion DIAN.
 */
@Data
public class DianResolutionRequest {

    @NotBlank(message = "El numero de resolucion es obligatorio")
    private String resolutionNumber;

    @NotBlank(message = "El prefijo es obligatorio")
    private String prefix;

    @NotNull(message = "El numero inicial es obligatorio")
    @Positive(message = "El numero inicial debe ser positivo")
    private Long startNumber;

    @NotNull(message = "El numero final es obligatorio")
    @Positive(message = "El numero final debe ser positivo")
    private Long endNumber;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate startDate;

    @NotNull(message = "La fecha de finalizacion es obligatoria")
    private LocalDate endDate;

    private String technicalKey;

    private DianResolutionStatus status;

    // QA CXC Bug 5 (2026-06-03 / IEEE AR-RF-17): las notas admiten maximo 500.
    @Size(max = 500, message = "Las notas no pueden superar los 500 caracteres")
    private String notes;
}
