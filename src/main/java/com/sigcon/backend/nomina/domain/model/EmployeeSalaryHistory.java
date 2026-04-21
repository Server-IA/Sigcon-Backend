package com.sigcon.backend.nomina.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * HU-NOM-01 E3: historial de cambios salariales.
 *
 * <p>Cada vez que se modifica el {@code baseSalary} de un empleado se inserta
 * un registro aqui con el valor anterior, el nuevo, la fecha efectiva y el
 * motivo del cambio. El registro es append-only: no se edita ni se borra
 * logicamente (sin {@code @SQLDelete}), pero se mantiene {@code deleted_at}
 * por consistencia con el resto del esquema.
 *
 * <p>Las liquidaciones pasadas NO se recalculan al cambiar el salario
 * (inmutabilidad de HU-NOM-04 E2). Los calculos futuros usan el salario
 * vigente segun la {@code effectiveDate} mas reciente &le; fecha de liquidacion.
 */
@Entity
@Table(name = "employee_salary_history")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeSalaryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "previous_salary", nullable = false, precision = 20, scale = 2)
    private BigDecimal previousSalary;

    @Column(name = "new_salary", nullable = false, precision = 20, scale = 2)
    private BigDecimal newSalary;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "changed_by", length = 150)
    private String changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @jakarta.persistence.PrePersist
    protected void __onCreateTenant() {
        if (this.companyId == null) {
            this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        }
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
