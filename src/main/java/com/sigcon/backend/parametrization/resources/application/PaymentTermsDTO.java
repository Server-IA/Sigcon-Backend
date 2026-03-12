package com.sigcon.backend.parametrization.resources.application;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentTermsDTO {

    private Long id;

    @NotBlank(message = "El nombre es requerido")
    @Schema(description = "Nombre del término de pago", example = "30 días")
    private String name;

    @NotNull(message = "Los días son requeridos")
    @Min(value = 1, message = "Los días deben ser mayor a 0")
    @Max(value = 365, message = "Los días deben ser menor a 365")
    @Schema(description = "Días del término de pago", example = "30")
    private Integer days;

    @Schema(description = "Fecha de creación", example = "2026-03-11T10:00:00")
    private LocalDateTime createdAt;
    @Schema(description = "Fecha de actualización", example = "2026-03-11T10:00:00")
    private LocalDateTime updatedAt;
    @Schema(description = "Fecha de eliminación", example = "2026-03-11T10:00:00")
    private LocalDateTime deletedAt;
}
