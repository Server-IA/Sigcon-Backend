package com.sigcon.backend.invoices.purchase_orders.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AP-22: Request para rechazar o devolver una recepcion de bienes/servicios.
 * El motivo es obligatorio (minimo 20 caracteres) para garantizar trazabilidad.
 */
@Data
@NoArgsConstructor
public class RejectGoodsReceiptRequest {

    /** Motivo del rechazo/devolucion. Minimo 20 caracteres, maximo 500. */
    @NotBlank(message = "El motivo del rechazo es obligatorio")
    @Size(min = 20, max = 500,
          message = "El motivo del rechazo debe tener entre 20 y 500 caracteres")
    private String reason;
}
