package com.sigcon.backend.accounts_receivable.reports.application;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AR-10: Bucket de aging de cartera. Agrupa facturas por rango de dias de mora.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArAgingBucketDTO {

    /** Nombre del bucket (ej. "0-30", "31-60", "61-90", "+90"). */
    private String bucket;

    /** Total del saldo pendiente en el bucket. */
    private BigDecimal totalBalance;

    /** Numero de facturas en el bucket. */
    private Integer invoiceCount;

    /** Facturas del bucket. */
    private List<ArInvoiceReportRow> invoices;
}
