package com.sigcon.backend.integration.apikeys.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PA-RF-28 (Pendientes PA, 2026-06-03): credencial AAEF con ciclo de vida.
 *
 * <p>Se almacena UNICAMENTE el hash SHA-256 de la clave (jamas el texto plano).
 * La clave completa se muestra una sola vez al generarla. El {@code prefix}
 * (parte publica, p.ej. {@code SIGCON-AAEF-<publicId>}) permite identificar la
 * credencial en los listados sin revelar el secreto.
 *
 * <p>NO lleva {@code @Filter("tenantFilter")}: es un recurso administrado por
 * PLATFORM_ADMIN de forma cross-empresa (igual que {@code Company}), y el
 * {@code ApiKeyFilter} la valida durante el flujo AAEF cuando no hay
 * {@code TenantContext} establecido (la autenticacion aun no ocurrio).
 */
@Entity
@Table(name = "api_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** SHA-256 (hex) de la clave completa. */
    @Column(name = "key_hash", nullable = false, length = 255)
    private String keyHash;

    /** Parte publica de la clave (no secreta), para identificarla en los listados. */
    @Column(name = "prefix", nullable = false, length = 40)
    private String prefix;

    /** ACTIVE | REVOKED | EXPIRED. */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revocation_reason", length = 200)
    private String revocationReason;

    @Column(name = "created_by")
    private Long createdBy;

    /** Marca que ya se notifico el aviso de proxima expiracion (evita spam). */
    @Column(name = "notified_expiry", nullable = false)
    @Builder.Default
    private Boolean notifiedExpiry = false;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String STATUS_EXPIRED = "EXPIRED";
}
