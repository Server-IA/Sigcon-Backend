package com.sigcon.backend.accounts_receivable.sales_invoices.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoiceStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta con la informacion completa de una factura de venta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesInvoiceDTO {
    private Long id;
    private String invoiceNumber;
    private Long thirdPartyId;
    private String thirdPartyName;
    private String thirdPartyNit;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private Long currencyId;
    private String currencyIso;
    private BigDecimal exchangeRate;
    private Long paymentFormId;
    private String paymentFormName;
    private BigDecimal subtotal;
    private BigDecimal totalTax;
    private BigDecimal totalWithholding;
    private BigDecimal totalAmount;
    private BigDecimal balanceDue;
    private SalesInvoiceStatus status;
    private String notes;
    private String resolutionNumber;
    private String cufe;
    private Boolean xmlSent;
    private Long journalEntryId;
    private List<SalesInvoiceLineDTO> lines;
    /** HU-AR-01A E6: estado fiscal DIAN (PENDING/SENT/ACCEPTED/REJECTED/VOIDED). */
    private String dianStatus;
    /** HU-AR-01A E6: motivo de rechazo o mensaje DIAN si aplica. */
    private String dianMessage;
    /** HU-AR-01B E5: origen de la factura (MANUAL o AAEF). */
    private String source;
    /** HU-AR-01B E5: identificador externo de AgroFusion (cuando source=AAEF). */
    private String externalId;
}
