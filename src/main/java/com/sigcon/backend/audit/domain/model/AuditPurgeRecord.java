package com.sigcon.backend.audit.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * HU-AU-10 E5/E6: Evidencia inmutable de cada operacion de purga ejecutada.
 *
 * <p>Por cada lote purgado se registra: fecha, cantidad de registros purgados,
 * rango temporal afectado, y un hash SHA-256 del lote completo (concatenacion
 * de hashes individuales de cada log purgado). Este hash sirve como evidencia
 * de integridad para auditorias posteriores.
 */
@Entity
@Table(name = "audit_purge_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditPurgeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purge_date", nullable = false)
    private LocalDateTime purgeDate;

    @Column(name = "records_purged", nullable = false)
    private Integer recordsPurged;

    @Column(name = "oldest_purged")
    private LocalDateTime oldestPurged;

    @Column(name = "newest_purged")
    private LocalDateTime newestPurged;

    /** Hash SHA-256 de la concatenacion de hashes del lote (HU-AU-10 E5). */
    @Column(name = "batch_hash", nullable = false, length = 64)
    private String batchHash;

    @Column(name = "executed_by", length = 150)
    private String executedBy;

    @Column(name = "notes", length = 1000)
    private String notes;

    @PrePersist
    void pre() {
        if (purgeDate == null) purgeDate = LocalDateTime.now();
    }
}
