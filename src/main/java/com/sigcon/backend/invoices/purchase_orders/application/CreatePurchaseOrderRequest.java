package com.sigcon.backend.invoices.purchase_orders.application;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para crear o actualizar una orden de compra.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreatePurchaseOrderRequest {

    /** ID del proveedor al que se le emite la orden. */
    @NotNull(message = "El proveedor es obligatorio")
    private Long thirdPartyId;

    /** Fecha de emision de la orden. */
    @NotNull(message = "La fecha de la orden es obligatoria")
    private LocalDate orderDate;

    /** Fecha estimada de entrega. */
    private LocalDate deliveryDate;

    /** Observaciones de la orden. */
    private String notes;

    /** Lineas de detalle de la orden. */
    @NotEmpty(message = "La orden debe tener al menos una linea de detalle")
    @Valid
    private List<CreatePurchaseOrderLineRequest> lines;
}
