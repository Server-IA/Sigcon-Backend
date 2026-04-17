package com.sigcon.backend.general.accounting.journal.application;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para una linea de detalle de asiento contable.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JournalEntryLineDTO {

    private Long id;
    private Integer lineOrder;
    private Long accountingAccountId;
    private String accountCode;
    private String accountName;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String description;
    private String thirdPartyNit;
    private Long costCenterId;
    private String costCenterName;
}
