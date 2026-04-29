package com.sigcon.backend.audit.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * HU-AU-08 E4 (2026-04-28): Hallazgo de auditoria.
 *
 * <p>Un hallazgo es una observacion documentada sobre un evento de auditoria
 * que requiere seguimiento. Tiene un flujo de estados:
 * <pre>
 *   ABIERTO -> EN_REVISION -> CERRADO
 * </pre>
 *
 * <p>Cada hallazgo se vincula a un {@link AuditLog} via {@code auditLogId}.
 * El usuario auditor registra el hallazgo, lo asigna a un revisor y al
 * cerrarlo registra la conclusion.
 *
 * <p>Multi-tenant: campo {@code companyId} con auto-inyeccion en
 * {@code @PrePersist} y {@code @PostLoad} guard cross-tenant.
 */
@Entity
@Table(name = "audit_findings")
@SQLDelete(sql = "UPDATE audit_findings SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuditFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Log de auditoria al que se vincula el hallazgo. */
    @Column(name = "audit_log_id", nullable = false)
    private Long auditLogId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    /**
     * Estado del flujo: ABIERTO | EN_REVISION | CERRADO.
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ABIERTO";

    /**
     * Severidad del hallazgo (LOW/MEDIUM/HIGH/CRITICAL). Independiente de la
     * severidad del log, el auditor puede subirla o bajarla.
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String severity = "MEDIUM";

    /** Email del usuario que abrio el hallazgo. */
    @Column(name = "opened_by", length = 100)
    private String openedBy;

    /** Email del usuario asignado para revisar. */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    /** Email del usuario que cerro el hallazgo. */
    @Column(name = "closed_by", length = 100)
    private String closedBy;

    /** Conclusion / resolucion al cerrar. */
    @Column(name = "resolution", length = 2000)
    private String resolution;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "review_started_at")
    private LocalDateTime reviewStartedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        if (this.companyId == null) {
            this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        }
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.openedAt == null) this.openedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PostLoad
    protected void __onLoadTenant() {
        if (com.sigcon.backend.platform.tenant.TenantContext.isPlatformAdmin()) return;
        Long current = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        if (current == null || this.companyId == null) return;
        if (!current.equals(this.companyId)) {
            throw new com.sigcon.backend.platform.tenant.TenantIsolationException(
                    "Cross-tenant access blocked on AuditFinding id=" + this.id);
        }
    }
}
