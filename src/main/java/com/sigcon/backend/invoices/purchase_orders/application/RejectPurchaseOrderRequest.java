package com.sigcon.backend.invoices.purchase_orders.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para rechazar una orden de compra.
 *
 * HU-AP-17 E4 (Bloque AR): el motivo es obligatorio y debe tener al menos
 * 10 caracteres para evitar mensajes vacios o triviales que no documenten
 * la decision contable.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RejectPurchaseOrderRequest {

    /** Razon del rechazo (obligatoria, minimo 10 caracteres - HU-AP-17 E4). */
    @NotBlank(message = "Debe ingresar el motivo del rechazo para continuar")
    @Size(min = 10, message = "El motivo del rechazo debe tener al menos 10 caracteres")
    private String rejectionReason;
}
