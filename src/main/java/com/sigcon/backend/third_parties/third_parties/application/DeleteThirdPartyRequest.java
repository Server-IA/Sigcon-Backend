package com.sigcon.backend.third_parties.third_parties.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TER-10: Request para eliminacion de tercero con justificacion obligatoria.
 * La justificacion debe tener entre 50 y 500 caracteres.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeleteThirdPartyRequest {

    @NotBlank(message = "La justificacion es obligatoria para eliminar un tercero.")
    @Size(min = 50, max = 500, message = "La justificacion debe tener al menos 50 caracteres y maximo 500.")
    private String justification;
}
