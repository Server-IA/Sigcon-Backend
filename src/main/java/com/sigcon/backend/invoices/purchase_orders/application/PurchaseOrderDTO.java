package com.sigcon.backend.invoices.purchase_orders.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para ordenes de compra.
 * Incluye datos del proveedor y las lineas de detalle.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseOrderDTO {

    /** Identificador de la orden. */
    private Long id;

    /** Numero consecutivo de la orden. */
    private String orderNumber;

    /** ID del proveedor. */
    private Long thirdPartyId;

    /** Nombre o razon social del proveedor. */
    private String thirdPartyName;

    /** Fecha de emision de la orden. */
    private LocalDate orderDate;

    /** Fecha estimada de entrega. */
    private LocalDate deliveryDate;

    /** Estado actual de la orden (DRAFT, PENDING, APPROVED, REJECTED, CLOSED). */
    private String status;

    /** Monto total de la orden. */
    private BigDecimal totalAmount;

    /** Observaciones de la orden. */
    private String notes;

    /** Lineas de detalle de la orden. */
    private List<PurchaseOrderLineDTO> lines;
}
