package com.sigcon.backend.invoices.purchase_orders.application;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para lineas de recepcion de bienes.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoodsReceiptLineDTO {

    /** Identificador de la linea de recepcion. */
    private Long id;

    /** ID de la linea de orden de compra. */
    private Long purchaseOrderLineId;

    /** Descripcion del item (de la linea de OC). */
    private String description;

    /** Cantidad recibida. */
    private BigDecimal quantityReceived;
}
