package com.sigcon.backend.invoices.ap_payments.application;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para aplicar un anticipo a una factura de compra.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplyAdvanceRequest {

    /** ID de la factura a la que se aplica el anticipo. */
    @NotNull(message = "El ID de la factura es obligatorio")
    private Long invoiceId;

    /** Monto a aplicar del anticipo. */
    @NotNull(message = "El monto a aplicar es obligatorio")
    private BigDecimal amount;
}
