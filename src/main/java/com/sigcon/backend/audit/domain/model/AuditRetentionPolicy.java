package com.sigcon.backend.audit.domain.model;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * HU-AU-10 E1: Politica de retencion configurable por modulo + severidad.
 *
 * <p>Define cuantos dias se conservan los logs antes de ser candidatos a purga.
 * El admin puede tener varias politicas para distintos casos (CRITICAL → 10 anios,
 * HIGH → 5 anios, etc.). Al insertar un nuevo log, se busca la politica que
 * matchea (especificidad: modulo + severidad > severidad sola > sin filtros) y
 * se calcula {@code retention_until = now + retentionDays}.
 */
@Entity
@Table(name = "audit_retention_policies")
@SQLDelete(sql = "UPDATE audit_retention_policies SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRetentionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** Modulo a matchear (null = aplica a todos). */
    @Enumerated(EnumType.STRING)
    @Column(name = "match_module", length = 10)
    private AuditModule matchModule;

    /** Severidad a matchear (null = aplica a todas). */
    @Enumerated(EnumType.STRING)
    @Column(name = "match_severity", length = 10)
    private AuditSeverity matchSeverity;

    /** Dias de retencion (Decreto 2649/1993 Art. 134: 10 anios = 3650 dias). */
    @Column(name = "retention_days", nullable = false)
    private Integer retentionDays;

    /** Norma o politica que justifica la retencion. */
    @Column(name = "legal_basis", length = 255)
    private String legalBasis;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void prePersist() {
        if (this.companyId == null) this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }

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
