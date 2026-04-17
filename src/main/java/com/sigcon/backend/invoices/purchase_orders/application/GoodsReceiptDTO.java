package com.sigcon.backend.invoices.purchase_orders.application;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para recepciones de bienes/servicios.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoodsReceiptDTO {

    /** Identificador de la recepcion. */
    private Long id;

    /** ID de la orden de compra asociada. */
    private Long purchaseOrderId;

    /** Numero de la orden de compra. */
    private String purchaseOrderNumber;

    /** Numero consecutivo de la recepcion. */
    private String receiptNumber;

    /** Fecha de la recepcion. */
    private LocalDate receiptDate;

    /** Estado de la recepcion. */
    private String status;

    /** ID de la factura vinculada (three-way match). */
    private Long invoiceId;

    /** Observaciones de la recepcion. */
    private String notes;

    /** Lineas de detalle de la recepcion. */
    private List<GoodsReceiptLineDTO> lines;
}
