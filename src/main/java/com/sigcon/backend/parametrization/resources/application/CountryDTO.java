package com.sigcon.backend.parametrization.resources.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountryDTO {
    private Long id;

    @Schema(description = "Nombre del país")
    @NotBlank(message = "El nombre del país es requerido")
    @Size(max = 255, message = "El nombre del país debe tener menos de 255 caracteres")
    private String name;

    @Schema(description = "Código del país")
    @NotBlank(message = "El código del país es requerido")
    @Size(max = 255, message = "El código del país debe tener menos de 255 caracteres")
    private String code;

    @Schema(description = "Municipalidades del país")
    private List<MunicipalityDTO> municipalities;

    @Schema(description = "Fecha de creación")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha de actualización")
    private LocalDateTime updatedAt;

    @Schema(description = "Fecha de eliminación")
    private LocalDateTime deletedAt;
}
