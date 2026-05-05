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

    // HU-TER-10 E6.0 (Bloque AN, 2026-05-04): mensaje literal del Excel.
    @NotBlank(message = "La justificacion debe tener al menos 50 caracteres")
    @Size(min = 50, max = 500, message = "La justificacion debe tener al menos 50 caracteres")
    private String justification;
}
