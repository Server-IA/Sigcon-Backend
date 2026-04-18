package com.sigcon.backend.integration.domain.repository;

import com.sigcon.backend.integration.domain.model.JwtAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Repositorio del log forense de tokens JWT validados (HU-INT-RF-11).
 */
@Repository
public interface JwtAuditLogRepository extends JpaRepository<JwtAuditLog, Long> {

    /**
     * Lista paginada con filtros opcionales (cualquiera nullable).
     * Ordenamiento por fecha DESC implicito por el Pageable + Sort en service.
     */
    @Query("SELECT j FROM JwtAuditLog j WHERE "
         + "(:result IS NULL OR j.result = :result) AND "
         + "(:subject IS NULL OR LOWER(j.subject) LIKE LOWER(CONCAT('%', :subject, '%'))) AND "
         + "(:kid IS NULL OR j.kid = :kid) AND "
         + "(:from IS NULL OR j.validatedAt >= :from) AND "
         + "(:to IS NULL OR j.validatedAt <= :to)")
    Page<JwtAuditLog> search(@Param("result") String result,
                              @Param("subject") String subject,
                              @Param("kid") String kid,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to,
                              Pageable pageable);

    long countByResult(String result);
}
