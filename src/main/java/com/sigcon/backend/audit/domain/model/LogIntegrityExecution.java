package com.sigcon.backend.audit.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * BNK-HU-065: registro de cada ejecucion del verificador de integridad de la
 * cadena de hashes del log de auditoria (job nocturno o bajo demanda).
 *
 * <p>Tabla append-only y GLOBAL (no multi-tenant): la verificacion recorre
 * todas las cadenas de {@code audit_logs} de todas las empresas.
 */
@Entity
@Table(name = "log_integridad_ejecuciones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogIntegrityExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "total_verified", nullable = false)
    private Long totalVerified;

    /** OK | RUPTURA | ERROR */
    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "first_broken_id")
    private Long firstBrokenId;

    @Column(name = "chain_breaks", nullable = false)
    private Long chainBreaks;

    @Column(name = "content_mismatches", nullable = false)
    private Long contentMismatches;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    /** SCHEDULER | MANUAL */
    @Column(name = "trigger_source", nullable = false, length = 20)
    private String triggerSource;

    @Column(name = "triggered_by", length = 255)
    private String triggeredBy;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @PrePersist
    void prePersist() {
        if (executedAt == null) executedAt = LocalDateTime.now();
    }
}
