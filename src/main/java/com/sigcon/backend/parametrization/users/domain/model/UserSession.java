package com.sigcon.backend.parametrization.users.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PA-RF-01 v3.0 (Control de Cambios PA, 2026-05-29): sesion activa de un
 * usuario. Cada login crea una fila con su refresh token (almacenado como
 * hash SHA-256), sessionId, y metadata del dispositivo (deviceId, userAgent,
 * IP). Soporta:
 * <ul>
 *   <li>Limite de 3 sesiones activas por usuario con estrategia FIFO
 *       (al exceder se revoca la mas antigua).</li>
 *   <li>Refresh token persistido + rotacion/expiracion.</li>
 *   <li>Revocacion masiva (al restablecer contrasena, desactivar empresa o
 *       PLATFORM_ADMIN).</li>
 * </ul>
 *
 * <p>La tabla la crea Hibernate ddl-auto (precedente: entidades nuevas de BNK).
 */
@Entity
@Table(name = "user_sessions",
        indexes = {
                @Index(name = "idx_user_sessions_user", columnList = "user_id"),
                @Index(name = "idx_user_sessions_refresh", columnList = "refresh_token_hash")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Identificador publico de la sesion (UUID). Viaja en el claim sessionId del JWT. */
    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    /** SHA-256 hex del refresh token. El token en claro solo se devuelve una vez al cliente. */
    @Column(name = "refresh_token_hash", nullable = false, length = 80)
    private String refreshTokenHash;

    @Column(name = "device_id", length = 200)
    private String deviceId;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** NULL = sesion activa. NOT NULL = revocada (logout, FIFO, reset, desactivacion). */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
