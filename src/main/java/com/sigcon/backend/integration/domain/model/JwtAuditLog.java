package com.sigcon.backend.integration.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * HU-INT-RF-11 (forensia): registro append-only de cada validacion de token JWT
 * realizada por {@code AgroFusionJwtFilter} en endpoints {@code /api/contabilidad/**}.
 *
 * <p>Permite responder preguntas forenses como:
 * <ul>
 *   <li>Que tokens se aceptaron en un rango horario?</li>
 *   <li>Cuantos requests rechazo el filtro por scope insuficiente?</li>
 *   <li>Que kid se usaron (detectar uso de claves rotadas)?</li>
 *   <li>Que IPs envian tokens validos vs invalidos?</li>
 * </ul>
 *
 * <p>NO tiene {@code @SQLDelete} ni {@code @Where}: es append-only (solo INSERT y SELECT).
 * El controller solo expone GET para consulta forense por admin.
 *
 * <p>Tabla: {@code jwt_audit_log} (creada en V9-E).
 */
@Entity
@Table(name = "jwt_audit_log")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JwtAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Key ID del JWK que firmo el token (header.kid). Util para forensia de rotacion. */
    @Column(length = 150)
    private String kid;

    /** Claim {@code sub} del JWT (usuario emisor). */
    @Column(length = 255)
    private String subject;

    /** Claim {@code iat} convertido a LocalDateTime. */
    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    /** Claim {@code exp} convertido a LocalDateTime. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Claim {@code iss}. */
    @Column(length = 500)
    private String issuer;

    /** Scope efectivo extraido (claim 'scope', 'scp' o 'scopes' agregados). */
    @Column(length = 255)
    private String scope;

    /** Resultado de la validacion:
     *  VALID | EXPIRED | INVALID_SCOPE | WRONG_ISSUER | INVALID_SIGNATURE | MALFORMED | JWKS_UNAVAILABLE.
     */
    @Column(nullable = false, length = 30)
    private String result;

    /** Detalle del error si la validacion fallo. */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** IP remota del request (puede estar enmascarada por proxy). */
    @Column(name = "remote_ip", length = 60)
    private String remoteIp;

    /** User-Agent del cliente. */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** Path del request. */
    @Column(name = "request_path", length = 255)
    private String requestPath;

    @Column(name = "validated_at", nullable = false)
    @Builder.Default
    private LocalDateTime validatedAt = LocalDateTime.now();
}
