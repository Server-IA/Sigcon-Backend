package com.sigcon.backend.parametrization.users.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PA-RF-01 v3.0 (Control de Cambios PA, 2026-05-29): historial de contrasenas
 * por usuario para impedir la reutilizacion de las ultimas 5. Append-only:
 * cada cambio de contrasena inserta una fila con el hash BCrypt.
 *
 * <p>La tabla la crea Hibernate ddl-auto a partir de esta entidad (no requiere
 * migracion Flyway dedicada, igual que las entidades nuevas de BNK Fase 4).
 */
@Entity
@Table(name = "password_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
