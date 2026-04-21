package com.sigcon.backend.nomina.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * HU-NOM-03 / HU-NOM-04: recibo de nomina de un empleado para un periodo.
 *
 * <p>Un recibo es la cabecera de la liquidacion. El detalle de devengados,
 * deducciones y aportes esta en {@link PayrollLine} vinculadas por FK.
 *
 * <p>Flujo de estados (HU-NOM-04):
 * <ol>
 *   <li>{@code DRAFT}: recien liquidado, editable. JE en estado DRAFT.</li>
 *   <li>{@code APPROVED}: supervisor aprobo. JE pasa a POSTED. No se puede modificar.</li>
 *   <li>{@code CLOSED}: pagado y cerrado. Inmutable definitivo. Para corregir,
 *       crear nomina complementaria (HU-NOM-04 E3).</li>
 * </ol>
 *
 * <p>Invariante: {@code netPay = totalEarnings - totalDeductions}. No incluye
 * aportes patronales porque los paga la empresa (no se descuentan del empleado).
 */
@Entity
@Table(name = "payroll_receipts")
@SQLDelete(sql = "UPDATE payroll_receipts SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayrollReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    /** MONTHLY | BIWEEKLY. */
    @Column(name = "period_type", nullable = false, length = 20)
    @Builder.Default
    private String periodType = "MONTHLY";

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "days_worked", nullable = false)
    @Builder.Default
    private Integer daysWorked = 30;

    @Column(name = "total_earnings", nullable = false, precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal totalEarnings = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "total_employer_contributions", nullable = false, precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal totalEmployerContributions = BigDecimal.ZERO;

    @Column(name = "net_pay", nullable = false, precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal netPay = BigDecimal.ZERO;

    /** DRAFT | APPROVED | CLOSED. */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    /** ID del JournalEntry generado. Referencia logica a journal_entries.id. */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "approved_by", length = 150)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "closed_by", length = 150)
    private String closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** true si el recibo esta en estado editable (DRAFT). */
    public boolean isEditable() {
        return "DRAFT".equals(status);
    }

    /** true si el recibo es inmutable (APPROVED o CLOSED). */
    public boolean isImmutable() {
        return "APPROVED".equals(status) || "CLOSED".equals(status);
    }

    @jakarta.persistence.PrePersist
    protected void __onCreateTenant() {
        if (this.companyId == null) this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
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
