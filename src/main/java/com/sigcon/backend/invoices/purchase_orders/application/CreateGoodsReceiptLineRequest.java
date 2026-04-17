package com.sigcon.backend.invoices.purchase_orders.application;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para una linea de recepcion de bienes.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateGoodsReceiptLineRequest {

    /** ID de la linea de orden de compra que se recibe. */
    @NotNull(message = "La linea de orden de compra es obligatoria")
    private Long purchaseOrderLineId;

    /** Cantidad recibida. */
    @NotNull(message = "La cantidad recibida es obligatoria")
    private BigDecimal quantityReceived;
}
