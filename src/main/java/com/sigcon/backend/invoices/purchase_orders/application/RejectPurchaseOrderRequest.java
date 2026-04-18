package com.sigcon.backend.invoices.purchase_orders.application;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para rechazar una orden de compra.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RejectPurchaseOrderRequest {

    /** Razon del rechazo (obligatoria). */
    @NotBlank(message = "La razon del rechazo es obligatoria")
    private String rejectionReason;
}
