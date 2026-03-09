package com.sigcon.backend.third_parties.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateThirdPartyRolesStatusRequest {

    @NotEmpty(message = "Debe seleccionar al menos un rol.")
    @Schema(description = "IDs de roles a asignar", example = "[1,2]")
    private List<Long> roleIds;

    @NotNull(message = "El estado es obligatorio.")
    @Schema(description = "ID de estado del tercero", example = "2")
    private Long statusId;

    @Schema(description = "Motivo requerido cuando el estado es BLOQUEADO (minimo 20 caracteres)", example = "Bloqueado por inconsistencia documental validada")
    private String blockingReason;
}
