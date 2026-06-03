package com.sigcon.backend.parametrization.users.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    private boolean used;

    // PA-RF-02 v3.0 (Control de Cambios PA, 2026-05-29): trazabilidad de la
    // solicitud de recuperacion. Columnas creadas por Hibernate ddl-auto.
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "device_id", length = 200)
    private String deviceId;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }


}
