package com.sigcon.backend.banks.bankaccounts.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request para eliminar o desactivar cuenta bancaria")
public class BankAccountDeleteRequest {

    @NotBlank(message = "El motivo es obligatorio")
    @Size(min = 5, message = "El motivo debe tener al menos 5 caracteres")
    @Schema(description = "Motivo de eliminación o desactivación", required = true)
    private String motivo;
}
