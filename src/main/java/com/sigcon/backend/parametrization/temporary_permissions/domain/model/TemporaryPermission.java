package com.sigcon.backend.parametrization.temporary_permissions.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * HU-PA-13/14/15/16/17 — permiso temporal asignado a un usuario.
 *
 * <p>Reglas de negocio (HU-PA-13):
 * <ul>
 *   <li>Vigencia maxima: 90 dias entre {@code startDate} y {@code endDate} (E2).</li>
 *   <li>Maximo 3 permisos temporales activos por usuario simultaneamente (E3).</li>
 *   <li>Justificacion obligatoria, minimo 30 caracteres (E4).</li>
 *   <li>{@code startDate} puede ser futura (E5): se programa la elevacion. Solo cuenta cuando NOW >= startDate.</li>
 *   <li>Aditivos: NUNCA sustituyen ni reducen los permisos del rol base (E6).</li>
 *   <li>No delegables: el receptor NO puede asignarlos a otros (E7).</li>
 * </ul>
 *
 * <p>Estados:
 * <ul>
 *   <li>{@code ACTIVE}: dentro de su ventana de vigencia.</li>
 *   <li>{@code REVOKED}: revocado manualmente antes de vencer (HU-PA-14).</li>
 *   <li>{@code EXPIRED}: vencido por el job nocturno (HU-PA-15).</li>
 * </ul>
 */
@Entity
@Table(name = "temporary_permissions")
@SQLDelete(sql = "UPDATE temporary_permissions SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemporaryPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Usuario receptor del permiso temporal. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** ID del permiso atomico (FK a permissions). */
    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    /** Code del permiso (denormalizado para queries rapidas y resilientes a renames). */
    @Column(name = "permission_code", nullable = false, length = 120)
    private String permissionCode;

    @Column(name = "granted_by_user_id")
    private Long grantedByUserId;

    @Column(name = "granted_by_email", length = 255)
    private String grantedByEmail;

    @Column(name = "justification", nullable = false, length = 500)
    private String justification;

    @Column(name = "start_date", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "revoked_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by_user_id")
    private Long revokedByUserId;

    @Column(name = "revoked_by_email", length = 255)
    private String revokedByEmail;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    /** Si ya se envio la notificacion de "vence en 24h" (HU-PA-15 E4). */
    @Column(name = "expired_notified_24h", nullable = false)
    @Builder.Default
    private Boolean expiredNotified24h = false;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime deletedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Status { ACTIVE, REVOKED, EXPIRED }
}
