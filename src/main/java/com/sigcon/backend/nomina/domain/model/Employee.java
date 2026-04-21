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
 * HU-NOM-01: Empleado del modulo de nomina.
 *
 * <p>Cada empleado esta asociado opcionalmente a un tercero (tabla {@code third_parties})
 * para reutilizar datos de contacto/documento, y a un centro de costo para la
 * imputacion contable de los gastos de nomina.
 *
 * <p>Los datos criticos para liquidacion son:
 * <ul>
 *   <li>{@code baseSalary}: base de calculo de todos los conceptos. Debe ser &ge; SMLV
 *       vigente (validacion HU-NOM-01 E2, CST Art. 145).</li>
 *   <li>{@code eps}, {@code pensionFund}: obligatorios para liquidar deducciones
 *       de seguridad social (HU-NOM-03 E3).</li>
 * </ul>
 *
 * <p>Soft delete estandar del proyecto: {@code deleted_at}.
 */
@Entity
@Table(name = "employees")
@SQLDelete(sql = "UPDATE employees SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    /** FK opcional a third_parties. Permite reutilizar datos de contacto. */
    @Column(name = "third_party_id")
    private Long thirdPartyId;

    @Column(name = "document_type", nullable = false, length = 10)
    private String documentType;

    @Column(name = "document_number", nullable = false, length = 50)
    private String documentNumber;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 150)
    private String position;

    /** INDEFINIDO | FIJO | OBRA_LABOR | PRESTACION_SERVICIOS. */
    @Column(name = "contract_type", length = 30)
    private String contractType;

    @Column(name = "base_salary", nullable = false, precision = 20, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(length = 150)
    private String eps;

    @Column(name = "pension_fund", length = 150)
    private String pensionFund;

    @Column(length = 150)
    private String arl;

    @Column(name = "compensation_box", length = 150)
    private String compensationBox;

    /** FK a cost_centers para imputar gastos de nomina. */
    @Column(name = "cost_center_id")
    private Long costCenterId;

    /** ACTIVE | INACTIVE | TERMINATED. */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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
