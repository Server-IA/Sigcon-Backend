package com.sigcon.backend.invoices.purchase_orders.application;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para crear una linea de detalle en una orden de compra.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreatePurchaseOrderLineRequest {

    /** Descripcion del bien o servicio solicitado. */
    @NotBlank(message = "La descripcion de la linea es obligatoria")
    private String description;

    /** Cantidad solicitada. */
    @NotNull(message = "La cantidad es obligatoria")
    private BigDecimal quantity;

    /** Precio unitario del item. */
    @NotNull(message = "El precio unitario es obligatorio")
    private BigDecimal unitPrice;
}
