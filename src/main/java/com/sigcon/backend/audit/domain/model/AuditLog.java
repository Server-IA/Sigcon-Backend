package com.sigcon.backend.audit.domain.model;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * HU-AU-01: Registro inmutable de evento de auditoria.
 *
 * <p>Tabla <b>append-only</b>: los registros no se pueden modificar ni eliminar
 * una vez creados. No tiene {@code deleted_at}, {@code @SQLDelete} ni {@code @Where}.
 * El hash SHA-256 encadenado garantiza la integridad de la cadena cronologica.
 *
 * <p>Campos enriquecidos con metadatos tecnicos (HU-AU-02): IP, User-Agent.
 * Vinculacion opcional con asiento contable (HU-AU-09) via {@code journalEntryId}.
 *
 * <p>Tabla: {@code audit_logs} (creada en migracion V9-10).
 */
@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Momento exacto del evento. */
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    /** ID del usuario que ejecuto la accion (null si sistema). */
    @Column(name = "user_id")
    private Long userId;

    /** Email del usuario (desnormalizado para consulta rapida). */
    @Column(name = "user_email", length = 255)
    private String userEmail;

    /** Tipo de accion realizada. */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private AuditAction action;

    /** Nombre de la entidad afectada (ej. "ThirdParty", "Invoice"). */
    @Column(name = "entity_type", length = 100)
    private String entityType;

    /** ID de la entidad afectada. */
    @Column(name = "entity_id")
    private Long entityId;

    /** Modulo de origen del evento. */
    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 10)
    private AuditModule module;

    /** Nivel de criticidad del evento. */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private AuditSeverity severity;

    /** Descripcion legible del evento. */
    @Column(name = "description", length = 500)
    private String description;

    /** Estado anterior de la entidad (JSON, solo para UPDATE). */
    @Column(name = "old_values", columnDefinition = "TEXT")
    private String oldValues;

    /** Estado nuevo de la entidad (JSON, para CREATE y UPDATE). */
    @Column(name = "new_values", columnDefinition = "TEXT")
    private String newValues;

    /** Direccion IP del cliente (HU-AU-02). */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** User-Agent del navegador/cliente (HU-AU-02). */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Hash SHA-256 de este registro = SHA256(previousHash + timestamp + action
     * + entityType + entityId + userId). Garantiza inmutabilidad (HU-AU-01 E6).
     */
    @Column(name = "hash", nullable = false, length = 64)
    private String hash;

    /**
     * Hash del registro inmediatamente anterior en la cadena.
     * El primer registro usa "GENESIS" como previous_hash.
     */
    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    /** FK logica al asiento contable vinculado (HU-AU-09). */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    /**
     * HU-AU-10 E7: Si true, el registro NO puede ser purgado aunque su
     * retencion haya expirado. Lo activa el admin para evidencias en proceso
     * judicial/auditoria.
     */
    @Column(name = "legal_hold", nullable = false)
    @Builder.Default
    private Boolean legalHold = false;

    /** Razon del legal hold (auditoria del flag). */
    @Column(name = "legal_hold_reason", length = 500)
    private String legalHoldReason;

    /**
     * HU-AU-10 E2: Fecha hasta la cual el registro debe conservarse. Calculada
     * al insertar segun la politica de retencion aplicable.
     */
    @Column(name = "retention_until")
    private LocalDateTime retentionUntil;

    /** HU-AU-10 E3: Si distinto de null, el registro fue purgado. */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    /** Hash SHA-256 del lote en el que fue purgado (evidencia HU-AU-10 E5). */
    @Column(name = "archived_hash", length = 64)
    private String archivedHash;

    @PrePersist
    void prePersist() {
        if (timestamp == null) timestamp = LocalDateTime.now();
    }
}
