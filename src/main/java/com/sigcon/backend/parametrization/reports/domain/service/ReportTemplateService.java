package com.sigcon.backend.parametrization.reports.domain.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sigcon.backend.parametrization.reports.application.ReportTemplateDTO;
import com.sigcon.backend.parametrization.reports.application.ReportTemplateRequest;
import com.sigcon.backend.parametrization.reports.domain.model.ReportTemplate;
import com.sigcon.backend.parametrization.reports.domain.model.ReportType;
import com.sigcon.backend.parametrization.reports.domain.repository.ReportTemplateRepository;
import com.sigcon.backend.parametrization.reports.domain.repository.ReportTypeRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de logica de negocio para plantillas de reporte (HU-PA-RF-38/39/40).
 * Gestiona CRUD con versionamiento automatico, vigencia temporal, marca "por defecto"
 * y archivo binario adjunto.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportTemplateService {

    /** HU-PA-RF-39 E1: tipos MIME aceptados para el archivo de plantilla. */
    private static final Set<String> ALLOWED_MIME = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "text/plain",
            "text/csv",
            "application/xml",
            "text/xml"
    );

    /** Tamaño maximo permitido: 10MB. */
    private static final long MAX_SIZE_BYTES = 10L * 1024L * 1024L;

    private final ReportTemplateRepository reportTemplateRepository;
    private final ReportTypeRepository reportTypeRepository;
    private final DataTableSpecificationBuilder<ReportTemplate> specificationBuilder =
            new DataTableSpecificationBuilder<>();

    /**
     * HU-PA-RF-38: Obtiene la lista paginada de plantillas con filtros dinamicos.
     */
    public ResponseEntity<?> getReportTemplates(DataTableRequest request) {
        try {
            int start = Math.max(0, request.getStart());
            int length = request.getLength();
            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                    ? Pageable.unpaged()
                    : PageRequest.of(page, safeLength);

            Specification<ReportTemplate> spec = specificationBuilder.build(request)
                    .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

            Page<ReportTemplate> templates = reportTemplateRepository.findAll(spec, pageable);

            return ResponseEntity.ok(
                    DataTableResponse.from(templates.map(this::toDto), request.getDraw())
            );
        } catch (Exception e) {
            log.error("Error al obtener plantillas de reporte: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
            );
        }
    }

    /**
     * HU-PA-RF-39: Crea una nueva plantilla de reporte con vigencia y archivo opcional.
     * <ul>
     *   <li>E1: valida tipo de reporte + captura version + vigencia + archivo.</li>
     *   <li>E2: bloquea duplicado de version/vigencia para el mismo tipo.</li>
     *   <li>E3: permite marcar como por defecto (unica por tipo).</li>
     * </ul>
     */
    @Transactional
    public ResponseEntity<?> storeReportTemplate(ReportTemplateRequest request, MultipartFile file) {
        try {
            ReportType reportType = reportTypeRepository.findById(request.getReportTypeId())
                    .orElse(null);

            if (reportType == null) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("El tipo de reporte asociado no existe."))
                );
            }

            if (request.getValidFrom() == null) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("La fecha de vigencia inicial es obligatoria."))
                );
            }
            if (request.getValidTo() != null && request.getValidTo().isBefore(request.getValidFrom())) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("La vigencia final no puede ser anterior a la inicial."))
                );
            }

            // HU-PA-RF-39 E2: solapamiento de vigencia
            // Sentinel 9999-12-31 para el caso sin fin de vigencia (evita null en query Postgres).
            LocalDate validToForOverlap = request.getValidTo() != null
                    ? request.getValidTo()
                    : LocalDate.of(9999, 12, 31);
            if (reportTemplateRepository.existsOverlappingValidity(
                    request.getReportTypeId(),
                    request.getValidFrom(),
                    validToForOverlap)) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("Ya existe una plantilla con la misma version y vigencia para este tipo de reporte."))
                );
            }

            Integer maxVersion = reportTemplateRepository
                    .findMaxVersionByReportTypeId(request.getReportTypeId())
                    .orElse(0);

            // HU-PA-RF-39 E3: solo UNA por defecto por tipo
            boolean markAsDefault = Boolean.TRUE.equals(request.getIsDefault());
            if (markAsDefault) {
                reportTemplateRepository.clearDefaultForType(request.getReportTypeId());
            }

            ReportTemplate.ReportTemplateBuilder builder = ReportTemplate.builder()
                    .reportType(reportType)
                    .version(maxVersion + 1)
                    .filePath(request.getFilePath())
                    .description(request.getDescription())
                    .validFrom(request.getValidFrom())
                    .validTo(request.getValidTo())
                    .isDefault(markAsDefault)
                    .status("ACTIVE");

            if (file != null && !file.isEmpty()) {
                validateFile(file);
                try {
                    builder.fileName(file.getOriginalFilename())
                           .mimeType(file.getContentType())
                           .fileSize(file.getSize())
                           .fileContent(file.getBytes());
                } catch (IOException e) {
                    throw new IllegalStateException("Error leyendo el archivo: " + e.getMessage());
                }
            }

            ReportTemplate saved = reportTemplateRepository.save(builder.build());

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("Plantilla creada exitosamente."),
                            Optional.of(toDto(saved)))
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
            );
        } catch (Exception e) {
            log.error("Error al crear plantilla de reporte: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al crear la plantilla de reporte."))
            );
        }
    }

    /**
     * HU-PA-RF-40: Elimina (soft delete) una plantilla de reporte.
     * <ul>
     *   <li>E2: si es la unica plantilla activa del tipo, bloquea la eliminacion.</li>
     *   <li>E3: si la eliminada era la por-defecto y queda al menos otra, promueve
     *       a la mas antigua como nueva por-defecto.</li>
     * </ul>
     */
    @Transactional
    public ResponseEntity<?> deleteReportTemplate(Long id) {
        try {
            ReportTemplate template = reportTemplateRepository.findById(id)
                    .orElse(null);

            if (template == null) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("Plantilla de reporte no encontrada."))
                );
            }

            Long reportTypeId = template.getReportType().getId();
            long remaining = reportTemplateRepository.countByReportTypeIdAndDeletedAtIsNull(reportTypeId);
            if (remaining <= 1) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("No se puede eliminar la unica plantilla activa del tipo de reporte."))
                );
            }

            boolean wasDefault = Boolean.TRUE.equals(template.getIsDefault());
            reportTemplateRepository.delete(template);

            // HU-PA-RF-40 E3: promover nueva por-defecto si hace falta
            if (wasDefault) {
                List<ReportTemplate> others = reportTemplateRepository
                        .findByReportTypeIdAndDeletedAtIsNullOrderByCreatedAtAsc(reportTypeId);
                if (!others.isEmpty()) {
                    ReportTemplate promoted = others.get(0);
                    promoted.setIsDefault(true);
                    reportTemplateRepository.save(promoted);
                    log.info("Plantilla {} promovida como por-defecto del tipo {}", promoted.getId(), reportTypeId);
                }
            }

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("Plantilla de reporte eliminada correctamente."), Optional.empty())
            );
        } catch (Exception e) {
            log.error("Error al eliminar plantilla de reporte: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al eliminar la plantilla de reporte."))
            );
        }
    }

    /** HU-PA-RF-38: recupera una plantilla para descarga del archivo binario. */
    public ReportTemplate getForDownload(Long id) {
        return reportTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plantilla de reporte no encontrada"));
    }

    private void validateFile(MultipartFile file) {
        String mime = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!ALLOWED_MIME.contains(mime)) {
            throw new IllegalArgumentException(
                    "Tipo de archivo no permitido. Acepta PDF, Word, Excel, CSV, TXT o XML.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "El archivo supera el tamaño maximo permitido (10MB).");
        }
    }

    private ReportTemplateDTO toDto(ReportTemplate t) {
        return ReportTemplateDTO.builder()
                .id(t.getId())
                .reportTypeId(t.getReportType().getId())
                .reportTypeName(t.getReportType().getName())
                .version(t.getVersion())
                .filePath(t.getFilePath())
                .description(t.getDescription())
                .validFrom(t.getValidFrom())
                .validTo(t.getValidTo())
                .isDefault(t.getIsDefault())
                .hasFile(t.getFileSize() != null && t.getFileSize() > 0)
                .status(t.getStatus())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
