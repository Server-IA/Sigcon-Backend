package com.sigcon.backend.audit.domain.model;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * HU-AU-04: Regla configurable que sobrescribe la severidad automatica de
 * un evento de auditoria segun condiciones (modulo + accion + tipo entidad).
 *
 * <p>El admin puede definir reglas como "todo DELETE en CG es CRITICAL" o
 * "cualquier evento del modulo TER es MEDIUM". Las reglas se evaluan en orden
 * de {@code priority} DESC y la primera que matchea gana. Si ninguna regla
 * matchea, se aplica la clasificacion estatica por defecto del enum
 * {@link AuditAction}.
 */
@Entity
@Table(name = "audit_risk_rules")
@SQLDelete(sql = "UPDATE audit_risk_rules SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRiskRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** Modulo a matchear (null = cualquier modulo). */
    @Enumerated(EnumType.STRING)
    @Column(name = "match_module", length = 10)
    private AuditModule matchModule;

    /** Accion a matchear (null = cualquier accion). */
    @Enumerated(EnumType.STRING)
    @Column(name = "match_action", length = 20)
    private AuditAction matchAction;

    /** Tipo de entidad a matchear (null = cualquier entidad). */
    @Column(name = "match_entity_type", length = 100)
    private String matchEntityType;

    /** Severidad a asignar cuando la regla matchea. */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private AuditSeverity severity;

    /**
     * Prioridad: mayor valor = se evalua primero. Permite tener reglas
     * especificas (priority alta) que tomen precedencia sobre genericas.
     */
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 100;

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
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }
}
