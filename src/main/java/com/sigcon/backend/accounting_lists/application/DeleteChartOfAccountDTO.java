package com.sigcon.backend.accounting_lists.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "DTO para registrar el motivo de eliminacion/inactivacion de una cuenta")
public class DeleteChartOfAccountDTO {

    @NotBlank(message = "No ingreso el motivo de eliminacion")
    @Size(max = 255, message = "No ingreso el motivo de eliminacion")
    @Pattern(regexp = "^[A-Za-z0-9_\\-\\s]{1,255}$", message = "No ingreso el motivo de eliminacion")
    @Schema(description = "Motivo de la eliminacion o inactivacion", example = "Cuenta duplicada en catalogo")
    private String reason;
}
