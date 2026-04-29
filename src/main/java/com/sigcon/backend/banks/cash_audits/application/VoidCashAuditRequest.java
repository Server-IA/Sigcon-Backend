package com.sigcon.backend.banks.cash_audits.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HU-BNK-048 E2 - Request para anulacion logica de un arqueo APROBADO.
 * El motivo debe tener al menos 50 caracteres conforme la HU.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Datos requeridos para anular un arqueo APROBADO")
public class VoidCashAuditRequest {

    @NotBlank(message = "El motivo de anulacion es obligatorio")
    @Size(min = 50, max = 1000,
            message = "El motivo de anulacion debe tener al menos 50 caracteres")
    @Schema(description = "Motivo detallado de la anulacion (min 50 chars)",
            example = "Arqueo registrado con saldo fisico erroneo: el conteo final dio $X y se digito $Y por confusion del cajero")
    private String reason;
}
