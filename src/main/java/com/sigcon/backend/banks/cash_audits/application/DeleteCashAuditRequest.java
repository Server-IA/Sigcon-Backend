package com.sigcon.backend.banks.cash_audits.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HU-BNK-048 E1 - Request para eliminacion fisica de un arqueo en BORRADOR.
 * El motivo es obligatorio para registrar evidencia de auditoria (Decreto 2649/1993 Art. 57).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Datos requeridos para eliminar un arqueo en BORRADOR")
public class DeleteCashAuditRequest {

    @NotBlank(message = "El motivo de eliminacion es obligatorio")
    @Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres")
    @Schema(description = "Motivo de la eliminacion (min 10 chars)", example = "Arqueo registrado por error de digitacion")
    private String reason;
}
