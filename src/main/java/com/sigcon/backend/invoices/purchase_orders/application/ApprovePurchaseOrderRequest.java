package com.sigcon.backend.invoices.purchase_orders.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para aprobar una orden de compra.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApprovePurchaseOrderRequest {

    /** Observaciones opcionales de aprobacion. */
    private String notes;
}
