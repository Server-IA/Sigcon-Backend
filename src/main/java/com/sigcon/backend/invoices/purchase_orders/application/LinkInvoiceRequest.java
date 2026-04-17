package com.sigcon.backend.invoices.purchase_orders.application;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para vincular una factura a una recepcion de bienes (three-way match).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LinkInvoiceRequest {

    /** ID de la factura a vincular. */
    @NotNull(message = "El ID de la factura es obligatorio")
    private Long invoiceId;
}
