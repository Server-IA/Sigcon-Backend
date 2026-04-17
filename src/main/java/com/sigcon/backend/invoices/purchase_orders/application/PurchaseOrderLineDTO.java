package com.sigcon.backend.invoices.purchase_orders.application;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para lineas de orden de compra.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseOrderLineDTO {

    /** Identificador de la linea. */
    private Long id;

    /** Descripcion del bien o servicio. */
    private String description;

    /** Cantidad solicitada. */
    private BigDecimal quantity;

    /** Precio unitario. */
    private BigDecimal unitPrice;

    /** Total de la linea (quantity * unitPrice). */
    private BigDecimal totalLine;
}
