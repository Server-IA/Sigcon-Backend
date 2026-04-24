package com.sigcon.backend.parametrization.reports.domain.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sigcon.backend.parametrization.reports.application.ReportTypeDTO;
import com.sigcon.backend.parametrization.reports.application.ReportTypeRequest;
import com.sigcon.backend.parametrization.reports.domain.model.ReportType;
import com.sigcon.backend.parametrization.reports.domain.repository.ReportTemplateRepository;
import com.sigcon.backend.parametrization.reports.domain.repository.ReportTypeRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de logica de negocio para tipos de reporte.
 * Gestiona operaciones CRUD con validaciones de unicidad y dependencias.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportTypeService {

    private final ReportTypeRepository reportTypeRepository;
    private final ReportTemplateRepository reportTemplateRepository;
    private final AuditPublisher auditPublisher;
    private final DataTableSpecificationBuilder<ReportType> specificationBuilder =
            new DataTableSpecificationBuilder<>();

    /**
     * Obtiene la lista paginada de tipos de reporte con filtros dinamicos.
     *
     * @param request parametros de paginacion, filtros y ordenamiento
     * @return respuesta paginada con los tipos de reporte encontrados
     */
    public ResponseEntity<?> getReportTypes(DataTableRequest request) {
        try {
            int start = Math.max(0, request.getStart());
            int length = request.getLength();
            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                    ? Pageable.unpaged()
                    : PageRequest.of(page, safeLength);

            Specification<ReportType> spec = specificationBuilder.build(request)
                    .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

            Page<ReportType> reportTypes = reportTypeRepository.findAll(spec, pageable);

            return ResponseEntity.ok(
                    DataTableResponse.from(reportTypes.map(rt -> ReportTypeDTO.builder()
                            .id(rt.getId())
                            .name(rt.getName())
                            .description(rt.getDescription())
                            .status(rt.getStatus())
                            .createdAt(rt.getCreatedAt())
                            .updatedAt(rt.getUpdatedAt())
                            .build()), request.getDraw())
            );
        } catch (Exception e) {
            log.error("Error al obtener tipos de reporte: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
            );
        }
    }

    /**
     * Crea un nuevo tipo de reporte.
     * Valida que el nombre sea unico entre los registros activos.
     *
     * @param request datos del tipo de reporte a crear
     * @return respuesta exitosa o error de validacion
     */
    @Transactional
    public ResponseEntity<?> storeReportType(ReportTypeRequest request) {
        try {
            if (reportTypeRepository.existsByNameAndDeletedAtIsNull(request.getName())) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("El nombre del tipo de reporte ya existe."))
                );
            }

            ReportType reportType = ReportType.builder()
                    .name(request.getName())
                    .description(request.getDescription())
                    .status(request.getStatus() != null ? request.getStatus() : "ACTIVE")
                    .build();

            reportTypeRepository.save(reportType);
            auditPublisher.publishCreate(AuditModule.PA, "ReportType", reportType.getId(), "ReportType creado id=" + reportType.getId());

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("Tipo de reporte creado correctamente."), Optional.empty())
            );
        } catch (Exception e) {
            log.error("Error al crear tipo de reporte: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al crear el tipo de reporte."))
            );
        }
    }

    /**
     * Actualiza un tipo de reporte existente.
     * Valida que el nombre sea unico excluyendo el registro actual.
     *
     * @param id      identificador del tipo de reporte
     * @param request datos actualizados
     * @return respuesta exitosa o error de validacion
     */
    @Transactional
    public ResponseEntity<?> updateReportType(Long id, ReportTypeRequest request) {
        try {
            ReportType reportType = reportTypeRepository.findById(id)
                    .orElse(null);

            if (reportType == null) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("Tipo de reporte no encontrado."))
                );
            }

            if (reportTypeRepository.existsByNameAndIdNotAndDeletedAtIsNull(request.getName(), id)) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("El nombre del tipo de reporte ya existe."))
                );
            }

            reportType.setName(request.getName());
            reportType.setDescription(request.getDescription());
            if (request.getStatus() != null) {
                reportType.setStatus(request.getStatus());
            }

            reportTypeRepository.save(reportType);
            auditPublisher.publishUpdate(AuditModule.PA, "ReportType", reportType.getId(), "ReportType actualizado id=" + reportType.getId());

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("Tipo de reporte actualizado correctamente."), Optional.empty())
            );
        } catch (Exception e) {
            log.error("Error al actualizar tipo de reporte: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al actualizar el tipo de reporte."))
            );
        }
    }

    /**
     * Elimina (soft delete) un tipo de reporte.
     * Valida que no tenga plantillas activas asociadas antes de eliminar.
     *
     * @param id identificador del tipo de reporte
     * @return respuesta exitosa o error si tiene dependencias
     */
    @Transactional
    public ResponseEntity<?> deleteReportType(Long id) {
        try {
            ReportType reportType = reportTypeRepository.findById(id)
                    .orElse(null);

            if (reportType == null) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("Tipo de reporte no encontrado."))
                );
            }

            if (reportTemplateRepository.existsByReportTypeIdAndDeletedAtIsNull(id)) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("No se puede eliminar: tiene plantillas activas asociadas."))
                );
            }

            reportTypeRepository.delete(reportType);

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("Tipo de reporte eliminado correctamente."), Optional.empty())
            );
        } catch (Exception e) {
            log.error("Error al eliminar tipo de reporte: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al eliminar el tipo de reporte."))
            );
        }
    }
}
