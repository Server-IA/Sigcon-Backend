package com.sigcon.backend.invoices.ap_payments.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * RF-34 (Notas Tecnicas CXP): payload para reversar manualmente un pago/abono
 * de una factura de compra. El motivo es obligatorio y queda registrado en
 * auditoria.
 */
@Data
public class ReverseApPaymentRequest {

    @Schema(description = "Motivo de la reversion del pago",
            example = "Pago duplicado por error de captura")
    @NotBlank(message = "El motivo de la reversion es obligatorio")
    @Size(min = 10, message = "El motivo debe tener al menos 10 caracteres")
    private String reason;
}
