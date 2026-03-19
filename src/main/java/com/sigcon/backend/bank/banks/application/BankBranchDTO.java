package com.sigcon.backend.bank.banks.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para la gestión de sucursales bancarias")
public class BankBranchDTO {

    @Schema(description = "Identificador único de la sucursal", example = "1")
    private Long id;

    @NotBlank(message = "La dirección de la sucursal no puede ser nula")
    @Schema(description = "Dirección física de la sucursal", example = "Calle 50 # 10-20")
    private String address;

    @NotBlank(message = "La ciudad de la sucursal no puede ser nula")
    @Schema(description = "Ciudad donde se ubica la sucursal", example = "Bogotá")
    private String city;

    @Schema(description = "Indica si es la sucursal principal del banco", example = "false")
    private Boolean mainBranch;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotNull(message = "El ID del banco no puede ser nulo")
    @Schema(description = "ID del banco al que pertenece la sucursal", example = "1")
    private Long bankId;

    @Schema(description = "Fecha de creación del registro")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha de última actualización")
    private LocalDateTime updatedAt;
}