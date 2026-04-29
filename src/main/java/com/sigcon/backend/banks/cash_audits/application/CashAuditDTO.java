package com.sigcon.backend.banks.cash_audits.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.sigcon.backend.banks.cash_audits.domain.model.enums.CashAuditStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para arqueos de caja.
 * Incluye datos de la caja asociada para facilitar la visualizacion en frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashAuditDTO {

    private Long id;
    private Long cashId;
    private String cashCode;
    private String cashName;
    private LocalDate auditDate;
    private BigDecimal systemBalance;
    private BigDecimal physicalBalance;
    private BigDecimal difference;
    private CashAuditStatus status;
    private String notes;
    private Long supervisorId;
    private LocalDateTime approvedAt;
    private Long approvedBy;
    private Long journalEntryId;
    /** HU-BNK-048 E2 */
    private String voidReason;
    private LocalDateTime voidedAt;
    private Long voidedBy;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
