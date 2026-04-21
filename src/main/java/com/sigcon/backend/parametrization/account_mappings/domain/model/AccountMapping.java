package com.sigcon.backend.parametrization.account_mappings.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Mapeo de un concepto contable (ej. "AR_CLIENTES") a una cuenta contable (accounting_accounts)
 * que apunta a un codigo PUC especifico (ej. 1305).
 *
 * <p>Esta tabla resuelve la deuda tecnica de usar cuenta fallback/hardcoded en los
 * asientos contables (JournalEntry) generados automaticamente por los modulos AR, AP, BNK.
 *
 * <p>Los registros se siembran en la migracion V31 con los conceptos estandar del PUC
 * colombiano (Decreto 2650/1993). No se expone UI de edicion en esta version — los
 * mapeos son cuentas estandar por norma contable colombiana.
 *
 * <p>Patron: clave logica {@code concept_code} (inmutable), valor configurable
 * {@code accounting_account_id}.
 */
@Entity
@Table(name = "account_mappings")
@SQLDelete(sql = "UPDATE account_mappings SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    /** Codigo logico del concepto (ej. AR_CLIENTES, AP_PROVEEDORES). Inmutable. */
    @Column(name = "concept_code", nullable = false, length = 64)
    private String conceptCode;

    /** Descripcion legible del concepto. */
    @Column(name = "concept_description", nullable = false, length = 255)
    private String conceptDescription;

    /** Codigo PUC colombiano sugerido (documentacion, no FK). */
    @Column(name = "puc_code", nullable = false, length = 10)
    private String pucCode;

    /** Cuenta contable real a la que apunta el mapeo (FK logica a accounting_accounts.id). */
    @Column(name = "accounting_account_id", nullable = false)
    private Long accountingAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

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
