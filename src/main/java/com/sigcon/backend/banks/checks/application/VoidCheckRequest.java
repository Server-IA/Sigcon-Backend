package com.sigcon.backend.banks.checks.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VoidCheckRequest {

    @NotBlank(message = "El motivo de anulacion es obligatorio")
    @Size(min = 10, max = 500, message = "El motivo de anulacion debe tener entre 10 y 500 caracteres")
    private String voidReason;

    @NotBlank(message = "La contrasena de confirmacion es obligatoria")
    private String currentPassword;
}
