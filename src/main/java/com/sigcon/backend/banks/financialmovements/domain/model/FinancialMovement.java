package com.sigcon.backend.banks.financialmovements.domain.model;

import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.cash_management.domain.model.Cash;
import com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType;
import com.sigcon.backend.banks.reconciliation.domain.model.BankReconciliationSession;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_movements")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id")
    private BankAccount bankAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_id")
    private Cash cash;

    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    /**
     * Positivo: ingreso en cuenta/caja. Negativo: egreso (ej. débito por cheque pagado en extracto).
     */
    @Column(name = "amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    @Builder.Default
    private FinancialMovementSourceType sourceType = FinancialMovementSourceType.MANUAL;

    /**
     * Clasificacion del movimiento segun NIC 7: OPERATIVA, INVERSION, FINANCIACION.
     */
    @Column(name = "flow_activity", length = 20)
    private String flowActivity;

    @Column(name = "matched_check_id")
    private Long matchedCheckId;

    @Column(name = "matched_voucher_id")
    private Long matchedVoucherId;

    /**
     * QA-BLOQUE-AP (2026-04-29): emparejamiento alternativo con JournalEntry
     * cuando la empresa no usa Vouchers legacy. Mutuamente exclusivo con
     * matchedVoucherId (no se valida a nivel BD para no romper datos previos).
     */
    @Column(name = "matched_journal_entry_id")
    private Long matchedJournalEntryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_session_id")
    private BankReconciliationSession reconciliationSession;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.companyId == null) this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @jakarta.persistence.PostLoad
    protected void __onLoadTenant() {
        if (com.sigcon.backend.platform.tenant.TenantContext.isPlatformAdmin()) return;
        Long current = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        if (current == null || this.companyId == null) return;
        if (!current.equals(this.companyId)) {
            throw new com.sigcon.backend.platform.tenant.TenantIsolationException(
                    "Recurso fuera del tenant actual");
        }
    }
}
