package com.sigcon.backend.lists_accounting.cost_centers.application;

import com.sigcon.backend.lists_accounting.cost_centers.domain.model.enums.CostCenterStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CostCenterDTO {
    private Long id;

    @Size(min = 1, max = 20, message = "El código debe tener entre 1 y 20 caracteres")
    @Schema(description = "Código del centro de costo", example = "CC-001")
    @NotBlank(message = "El código es obligatorio")
    // HU-CFG-17 E3 (2026-04-27): solo alfanumerico ASCII + _ -. Rechaza Ñ, +, espacios.
    @Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$",
            message = "El código solo admite letras (A-Z), números, guion bajo y guion (sin Ñ, espacios ni caracteres especiales)")
    private String code;

    @Schema(description = "Nombre del centro de costo", example = "Centro de costo 1")
    @Size(min = 1, max = 50, message = "El nombre debe tener entre 1 y 50 caracteres")
    @NotBlank(message = "El nombre es obligatorio")
    // HU-CFG-17 MT-2 + CFG-19 MT-01: rechazo de tags HTML/XSS.
    @Pattern(regexp = "^[^<>]+$",
            message = "El nombre no puede contener los caracteres < o >")
    private String name;

    @Schema(description = "Descripción del centro de costo", example = "Descripción del centro de costo")
    @Pattern(regexp = "^[^<>]*$",
            message = "La descripción no puede contener los caracteres < o >")
    private String description;


    @Schema(description = "Estado del centro de costo", example = "ACTIVE")
    @NotNull(message = "El estado es obligatorio")
    private CostCenterStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String deletionReason;
}
