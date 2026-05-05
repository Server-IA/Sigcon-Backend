package com.sigcon.backend.parametrization.notifications.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * HU-PA-18: catalogo global de eventos del sistema disponibles para suscripcion por rol.
 * Tabla read-only desde la UI; se modifica via migracion SQL.
 */
@Entity
@Table(name = "notification_event_catalog")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEventCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_key", nullable = false, unique = true, length = 80)
    private String eventKey;

    @Column(nullable = false, length = 20)
    private String module;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 500)
    private String description;

    /** HU-PA-18: si soporta umbral en dias (eventos de vencimiento). */
    @Column(name = "supports_threshold", nullable = false)
    @Builder.Default
    private Boolean supportsThreshold = false;

    @Column(name = "default_threshold_days")
    private Integer defaultThresholdDays;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
}
