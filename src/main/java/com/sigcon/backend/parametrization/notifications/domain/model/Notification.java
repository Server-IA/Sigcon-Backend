package com.sigcon.backend.parametrization.notifications.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * HU-PA-19/20: notificacion in-app generada por el sistema.
 *
 * <ul>
 *   <li>{@link Type#ROL_EVENT}: disparada por suscripcion de rol (HU-PA-19).</li>
 *   <li>{@link Type#USER_EVENT}: dirigida directamente al usuario (HU-PA-20). No configurable.</li>
 *   <li>{@link Type#SYSTEM}: emitida por jobs/scheduler.</li>
 * </ul>
 */
@Entity
@Table(name = "notifications")
@SQLDelete(sql = "UPDATE notifications SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, length = 20)
    private String module;

    @Column(name = "event_key", nullable = false, length = 80)
    private String eventKey;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "action_url", length = 500)
    private String actionUrl;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "source_type", length = 80)
    private String sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Severity severity = Severity.INFO;

    @Column(name = "read_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime readAt;

    /** HU-PA-24: notificacion deja de aparecer cuando expires_at <= NOW. */
    @Column(name = "expires_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime createdAt;

    @Column(name = "deleted_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime deletedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (expiresAt == null) expiresAt = createdAt.plusDays(30);
    }

    public enum Type { ROL_EVENT, USER_EVENT, SYSTEM }
    public enum Severity { INFO, WARNING, CRITICAL }
}
