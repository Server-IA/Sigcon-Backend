package com.sigcon.backend.general.accounting;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Periodo contable. Permite validar si un mes/anio esta abierto, cerrado o bloqueado
 * para operaciones contables (activos, NIIF, depreciacion, etc.)
 *
 * <p>Constraint {@code uk_accounting_periods_year_month} garantiza unicidad de
 * (year, month) - declarado tambien en migracion V9-C para BDs ya creadas.
 * Esto evita {@code NonUniqueResultException} en
 * {@code AccountingPeriodService.findByYearAndMonth}.
 */
@Entity
@Table(name = "accounting_periods",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_accounting_periods_company_year_month",
           columnNames = {"company_id", "year", "month"}
       ))
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class AccountingPeriod {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Multi-tenant (V10-B). Auto-inyectado en @PrePersist. */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer month;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AccountingPeriodStatus status = AccountingPeriodStatus.OPEN;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by")
    private String closedBy;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "locked_by")
    private String lockedBy;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.companyId == null) {
            this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        }
    }

    @PostLoad
    protected void onLoad() {
        if (com.sigcon.backend.platform.tenant.TenantContext.isPlatformAdmin()) return;
        Long current = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        if (current == null || this.companyId == null) return;
        if (!current.equals(this.companyId)) {
            throw new com.sigcon.backend.platform.tenant.TenantIsolationException(
                    "Recurso fuera del tenant actual");
        }
    }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    /** Indica si el periodo permite registrar operaciones. */
    public boolean isOpen() { return status == AccountingPeriodStatus.OPEN; }

    /** Indica si el periodo esta cerrado (puede reabrirse). */
    public boolean isClosed() { return status == AccountingPeriodStatus.CLOSED; }

    /** Indica si el periodo esta bloqueado permanentemente. */
    public boolean isLocked() { return status == AccountingPeriodStatus.LOCKED; }
}
