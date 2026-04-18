package com.sigcon.backend.accounts_receivable.sales_invoices.application;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para una linea de factura de venta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesInvoiceLineDTO {
    private Long id;
    private Long itemId;
    private String itemName;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discount;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal withholdingAmount;
    private BigDecimal total;
}
