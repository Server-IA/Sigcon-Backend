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

    /** ID de la factura vinculada (three-way match, campo legacy 1:1). */
    private Long invoiceId;

    /**
     * RF-19 (Notas Tecnicas CXP, 2026-06-02): cantidad de facturas distintas
     * asociadas a la recepcion (legacy invoiceId + enlaces N:M).
     */
    private Integer linkedInvoiceCount;

    /**
     * RF-19: etiqueta para la columna "Factura Asociada":
     * null si no tiene factura, "#{id}" si tiene una, "Multiple" si tiene varias.
     */
    private String invoiceLabel;

    /** Observaciones de la recepcion. */
    private String notes;

    /** Lineas de detalle de la recepcion. */
    private List<GoodsReceiptLineDTO> lines;
}
