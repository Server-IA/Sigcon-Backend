package com.sigcon.backend.parametrization.resources.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MunicipalityDTO {
    private Long id;

    @Schema(description = "Nombre del municipio")
    @NotBlank(message = "El nombre del municipio es requerido")
    @Size(max = 255, message = "El nombre del municipio debe tener menos de 255 caracteres")
    private String name;

    @Schema(description = "Código del municipio")
    @NotBlank(message = "El código del municipio es requerido")
    @Size(max = 255, message = "El código del municipio debe tener menos de 255 caracteres")
    private String code;

    @Schema(description = "País del municipio")
    private CountryDTO country;

    @Schema(description = "Fecha de creación")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha de actualización")
    private LocalDateTime updatedAt;

        
    private LocalDateTime deletedAt;
}
