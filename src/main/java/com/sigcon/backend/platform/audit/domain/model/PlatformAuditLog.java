package com.sigcon.backend.platform.audit.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * HU-PA-PLAT-08: Log de auditoria EXCLUSIVO de plataforma.
 *
 * <p>Este log es independiente del audit_logs por empresa (modulo Auditoria).
 * Aqui se registran SOLO las acciones cross-tenant ejecutadas desde el panel
 * de plataforma (PLATFORM_ADMIN):
 * <ul>
 *   <li>COMPANY_CREATED / COMPANY_STATUS_CHANGED / COMPANY_REPROVISIONED</li>
 *   <li>PLATFORM_USER_CREATED / PLATFORM_USER_UPDATED / PLATFORM_USER_DEACTIVATED</li>
 *   <li>API_KEY_ROTATED</li>
 *   <li>AAEF_BATCH_RETRIED</li>
 *   <li>PLATFORM_AUDIT_EXPORTED</li>
 * </ul>
 *
 * <p>HU-PA-PLAT-08 E5 (inmutabilidad): la tabla tiene trigger de BD que bloquea
 * UPDATE y DELETE. Por construccion no exponemos {@code @SQLDelete} ni getters
 * de mutacion, y el listener Spring solo invoca {@code save()} para INSERT.
 */
@Entity
@Table(name = "audit_log_platform")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime occurredAt;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    /**
     * Codigo discreto de accion. Ver javadoc de la clase para valores soportados.
     */
    @Column(name = "action", nullable = false, length = 80)
    private String action;

    @Column(name = "target_type", length = 60)
    private String targetType;

    @Column(name = "target_id", length = 120)
    private String targetId;

    @Column(name = "target_label", length = 255)
    private String targetLabel;

    /**
     * Payload JSON con el contexto de la accion. Ej.: si action=COMPANY_CREATED,
     * payload tiene {nit, businessName, durationMs}.
     *
     * <p>Nota: se usa TEXT (no JSONB) para evitar el adapter de JSON de Hibernate 6
     * que choca con String + JdbcTypeCode. La (de)serializacion la hace Jackson en
     * el service layer.
     */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "remote_ip", length = 64)
    private String remoteIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** Duracion del aprovisionamiento si aplica (HU-PA-PLAT-01 E7). */
    @Column(name = "duration_ms")
    private Long durationMs;
}
