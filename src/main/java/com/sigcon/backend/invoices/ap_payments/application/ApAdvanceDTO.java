package com.sigcon.backend.invoices.ap_payments.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para anticipos a proveedores.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApAdvanceDTO {

    private Long id;
    private Long thirdPartyId;
    private String thirdPartyName;
    private BigDecimal amount;
    private LocalDate advanceDate;
    private String status;
    private Long appliedInvoiceId;
    private BigDecimal appliedAmount;
    /** AP-RF-05 E6: monto aun disponible del anticipo (amount - appliedAmount). */
    private BigDecimal availableAmount;
}
