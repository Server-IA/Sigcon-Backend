package com.sigcon.backend.parametrization.notifications.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * HU-PA-18: suscripcion de un rol a un evento del catalogo, con umbral opcional en dias.
 *
 * <p>Si {@code companyId} es NULL la suscripcion es global (no usado en V1).
 * En V1 las suscripciones siempre tienen company_id porque los roles son por tenant.
 */
@Entity
@Table(name = "role_notification_subscriptions")
@SQLDelete(sql = "UPDATE role_notification_subscriptions SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleNotificationSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "event_key", nullable = false, length = 80)
    private String eventKey;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "threshold_days")
    private Integer thresholdDays;

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
}
