package com.sigcon.backend.parametrization.reports.interfaces.controller;

import java.time.LocalDate;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.sigcon.backend.parametrization.reports.application.ReportTemplateRequest;
import com.sigcon.backend.parametrization.reports.domain.model.ReportTemplate;
import com.sigcon.backend.parametrization.reports.domain.service.ReportTemplateService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para la gestion de plantillas de reporte (HU-PA-RF-38/39/40).
 * Proporciona endpoints para listar, crear (multipart con archivo), descargar y eliminar.
 */
@RestController
@RequestMapping("/api/report-templates")
@RequiredArgsConstructor
@Tag(name = "1. Módulo de Parametrización - Tipos de Reporte y Plantillas",
        description = "Endpoints para gestión de plantillas de reporte")
public class ReportTemplateController {

    private final ReportTemplateService reportTemplateService;

    /**
     * HU-PA-RF-38: Lista paginada de plantillas de reporte con filtros.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_REPORT_TEMPLATES') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Listar plantillas de reporte", description = "Obtiene la lista paginada de plantillas con filtros")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de plantillas obtenida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Parametros de paginacion invalidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public ResponseEntity<?> getReportTemplates(
            @RequestBody(required = false) DataTableRequest request) {
        return reportTemplateService.getReportTemplates(request);
    }

    /**
     * HU-PA-RF-39: Crea una nueva plantilla con version auto-incrementada, vigencia,
     * flag por defecto y archivo adjunto opcional (multipart/form-data).
     */
    @PostMapping(value = "/store", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_CREATE_REPORT_TEMPLATES') or hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Crear plantilla de reporte",
            description = "Registra una nueva plantilla con version auto-incrementada, vigencia y archivo adjunto"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plantilla creada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos invalidos, vigencia solapada o archivo no permitido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Tipo de reporte asociado no encontrado")
    })
    public ResponseEntity<?> storeReportTemplate(
            @Parameter(description = "ID del tipo de reporte") @RequestParam("reportTypeId") Long reportTypeId,
            @Parameter(description = "Descripcion opcional") @RequestParam(value = "description", required = false) String description,
            @Parameter(description = "Ruta externa opcional") @RequestParam(value = "filePath", required = false) String filePath,
            @Parameter(description = "Fecha inicio vigencia (YYYY-MM-DD)")
                @RequestParam("validFrom") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
            @Parameter(description = "Fecha fin vigencia (YYYY-MM-DD, opcional)")
                @RequestParam(value = "validTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validTo,
            @Parameter(description = "Marcar como plantilla por defecto")
                @RequestParam(value = "isDefault", required = false, defaultValue = "false") Boolean isDefault,
            @Parameter(description = "Archivo binario de la plantilla (opcional)")
                @RequestParam(value = "file", required = false) MultipartFile file) {

        ReportTemplateRequest request = ReportTemplateRequest.builder()
                .reportTypeId(reportTypeId)
                .description(description)
                .filePath(filePath)
                .validFrom(validFrom)
                .validTo(validTo)
                .isDefault(Boolean.TRUE.equals(isDefault))
                .build();

        return reportTemplateService.storeReportTemplate(request, file);
    }

    /**
     * HU-PA-RF-38: Descarga el archivo binario de una plantilla.
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('PERM_VIEW_REPORT_TEMPLATES') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Descargar archivo de plantilla")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Archivo descargado exitosamente"),
        @ApiResponse(responseCode = "404", description = "Plantilla no encontrada o sin archivo adjunto")
    })
    public ResponseEntity<?> downloadTemplate(@PathVariable Long id) {
        try {
            ReportTemplate t = reportTemplateService.getForDownload(id);
            if (t.getFileContent() == null || t.getFileContent().length == 0) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(
                                java.util.Optional.of("La plantilla no tiene archivo adjunto.")));
            }
            Resource resource = new ByteArrayResource(t.getFileContent());
            String filename = t.getFileName() != null ? t.getFileName() : ("plantilla_" + id);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(
                            t.getMimeType() != null ? t.getMimeType() : "application/octet-stream"))
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(java.util.Optional.of(e.getMessage())));
        }
    }

    /**
     * HU-PA-RF-40: Elimina (soft delete) una plantilla de reporte.
     */
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_REPORT_TEMPLATES') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Eliminar plantilla de reporte", description = "Elimina una plantilla de reporte (soft delete)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plantilla eliminada exitosamente"),
        @ApiResponse(responseCode = "400", description = "No se puede eliminar la unica plantilla activa"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Plantilla no encontrada")
    })
    public ResponseEntity<?> deleteReportTemplate(@PathVariable Long id) {
        return reportTemplateService.deleteReportTemplate(id);
    }
}
