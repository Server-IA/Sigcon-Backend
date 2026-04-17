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
 * Request para registrar una recepcion de bienes/servicios.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateGoodsReceiptRequest {

    /** ID de la orden de compra asociada. */
    @NotNull(message = "La orden de compra es obligatoria")
    private Long purchaseOrderId;

    /** Fecha de la recepcion. */
    @NotNull(message = "La fecha de recepcion es obligatoria")
    private LocalDate receiptDate;

    /** Observaciones de la recepcion. */
    private String notes;

    /** Lineas de detalle con cantidades recibidas. */
    @NotEmpty(message = "La recepcion debe tener al menos una linea")
    @Valid
    private List<CreateGoodsReceiptLineRequest> lines;
}
