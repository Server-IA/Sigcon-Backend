package com.sigcon.backend.audit.domain.repository;

import com.sigcon.backend.audit.domain.model.AuditLog;
import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para {@link AuditLog}. Append-only: no tiene metodos de
 * delete ni update.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>,
        JpaSpecificationExecutor<AuditLog> {

    /** Ultimo registro insertado (para obtener previous_hash). */
    Optional<AuditLog> findTopByOrderByIdDesc();

    /** Ultimos N eventos para el dashboard. */
    List<AuditLog> findTop10ByOrderByTimestampDesc();

    /** Historial de una entidad especifica (HU-AU-09). */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, Long entityId);

    /** Eventos vinculados a un asiento contable (HU-AU-09). */
    List<AuditLog> findByJournalEntryIdOrderByTimestampDesc(Long journalEntryId);

    /** Conteo por severidad desde una fecha (dashboard). */
    @Query("SELECT a.severity, COUNT(a) FROM AuditLog a WHERE a.timestamp >= :since GROUP BY a.severity")
    List<Object[]> countBySeveritySince(@Param("since") LocalDateTime since);

    /** Conteo por modulo desde una fecha (dashboard). */
    @Query("SELECT a.module, COUNT(a) FROM AuditLog a WHERE a.timestamp >= :since GROUP BY a.module")
    List<Object[]> countByModuleSince(@Param("since") LocalDateTime since);

    /** Conteo por accion desde una fecha (dashboard). */
    @Query("SELECT a.action, COUNT(a) FROM AuditLog a WHERE a.timestamp >= :since GROUP BY a.action")
    List<Object[]> countByActionSince(@Param("since") LocalDateTime since);

    /** Total de eventos registrados. */
    long count();

    /**
     * HU-AU-10 E2/E3: Logs candidatos a purga (retencion vencida + sin legal hold +
     * sin archived_at todavia). Limite para procesar en lotes.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.retentionUntil IS NOT NULL "
         + "AND a.retentionUntil <= :now "
         + "AND a.legalHold = false "
         + "AND a.archivedAt IS NULL "
         + "ORDER BY a.timestamp ASC")
    List<AuditLog> findPurgeable(@Param("now") LocalDateTime now,
                                  org.springframework.data.domain.Pageable pageable);

    /** Cuenta logs con legal_hold activo (para dashboard). */
    long countByLegalHoldTrue();
}
