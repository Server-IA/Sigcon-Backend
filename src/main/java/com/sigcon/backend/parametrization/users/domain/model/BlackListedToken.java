package com.sigcon.backend.parametrization.users.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blacklisted_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlackListedToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // QA Auditoria (2026-06-02): TEXT en vez de varchar(5000). El JWT crecio con
    // los claims de permisos y superaba los 5000 chars -> el logout fallaba con
    // DataIntegrityViolation y el evento LOGOUT no se registraba. La unicidad la
    // garantiza AuthService (existsByToken antes de insertar) + un indice HASH
    // (ver V9-Zzzzzo); el UNIQUE btree no soporta valores tan largos.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String token;

    // PA-RF-27 (Pendientes PA): jti (JWT ID) del token invalidado. Se almacena
    // de forma aditiva (el BlackListFilter sigue validando por `token`); el jti
    // queda para trazabilidad y futura migracion a invalidacion por jti.
    @Column(name = "jti", length = 255)
    private String jti;

    // PA-RF-27: expiracion del token (claim exp). El job de limpieza
    // (BlacklistCleanupScheduler) borra las filas con expires_at < now para que
    // la tabla no crezca indefinidamente.
    @Column(name = "expires_at")
    private java.time.LocalDateTime expiresAt;
}
