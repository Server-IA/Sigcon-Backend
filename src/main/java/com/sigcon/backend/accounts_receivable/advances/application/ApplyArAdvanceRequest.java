package com.sigcon.backend.accounts_receivable.advances.application;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para aplicar un anticipo de cliente a una factura de venta.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplyArAdvanceRequest {

    /** ID de la factura de venta a la que se aplica el anticipo. */
    @NotNull(message = "El ID de la factura es obligatorio")
    private Long invoiceId;

    /** Monto a aplicar del anticipo. */
    @NotNull(message = "El monto a aplicar es obligatorio")
    private BigDecimal amount;
}
