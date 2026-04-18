package com.sigcon.backend.parametrization.reports.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.parametrization.reports.domain.model.ReportTemplate;

/**
 * Repositorio JPA para la entidad ReportTemplate.
 * Soporta paginacion, filtros dinamicos, validacion de dependencia y versionamiento.
 */
@Repository
public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long>, JpaSpecificationExecutor<ReportTemplate> {

    /**
     * Verifica si existen plantillas activas (no eliminadas) asociadas a un tipo de reporte.
     * Se usa antes de eliminar un tipo de reporte para evitar inconsistencias.
     *
     * @param reportTypeId ID del tipo de reporte
     * @return true si existen plantillas activas para ese tipo
     */
    boolean existsByReportTypeIdAndDeletedAtIsNull(Long reportTypeId);

    /**
     * Obtiene la version maxima de plantillas para un tipo de reporte dado.
     * Permite auto-incrementar la version al crear una nueva plantilla.
     *
     * @param reportTypeId ID del tipo de reporte
     * @return la version maxima encontrada, o vacio si no hay plantillas
     */
    @Query("SELECT MAX(rt.version) FROM ReportTemplate rt WHERE rt.reportType.id = :reportTypeId")
    Optional<Integer> findMaxVersionByReportTypeId(@Param("reportTypeId") Long reportTypeId);

    /**
     * HU-PA-RF-39 E2: detecta solapamiento de vigencia para un tipo de reporte.
     * Dos plantillas se consideran duplicadas si comparten tipo de reporte y sus
     * periodos (validFrom..validTo) se superponen. validTo = NULL equivale a infinito.
     *
     * @return true si ya existe una plantilla con rango de vigencia solapado
     */
    /**
     * Nota: el service debe pasar {@code validToNew} = fecha infinito (9999-12-31)
     * cuando la plantilla nueva no tiene fin de vigencia, y esta query trata internamente
     * {@code rt.validTo IS NULL} como infinito. Esto evita ambiguedad de tipos en Postgres.
     */
    @Query("SELECT COUNT(rt) > 0 FROM ReportTemplate rt " +
            "WHERE rt.reportType.id = :reportTypeId AND rt.deletedAt IS NULL " +
            "AND rt.validFrom IS NOT NULL " +
            "AND (rt.validTo IS NULL OR rt.validTo >= :validFromNew) " +
            "AND rt.validFrom <= :validToNew")
    boolean existsOverlappingValidity(
            @Param("reportTypeId") Long reportTypeId,
            @Param("validFromNew") LocalDate validFromNew,
            @Param("validToNew") LocalDate validToNew);

    /**
     * HU-PA-RF-39 E3 / HU-PA-RF-40 E3: obtiene plantillas por tipo de reporte.
     * Usado para la asignacion automatica de la plantilla por defecto cuando
     * se elimina la ultima marcada como default.
     */
    List<ReportTemplate> findByReportTypeIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long reportTypeId);

    /**
     * HU-PA-RF-39 E3: desmarca la plantilla por defecto previa de un tipo de reporte,
     * para garantizar unicidad antes de insertar la nueva.
     */
    @Modifying
    @Query("UPDATE ReportTemplate rt SET rt.isDefault = false " +
            "WHERE rt.reportType.id = :reportTypeId AND rt.isDefault = true AND rt.deletedAt IS NULL")
    int clearDefaultForType(@Param("reportTypeId") Long reportTypeId);

    /**
     * HU-PA-RF-40 E2: cuenta plantillas activas del tipo (para prevenir eliminar la ultima).
     */
    long countByReportTypeIdAndDeletedAtIsNull(Long reportTypeId);
}
