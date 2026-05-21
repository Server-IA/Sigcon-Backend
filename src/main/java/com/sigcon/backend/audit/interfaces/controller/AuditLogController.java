package com.sigcon.backend.audit.interfaces.controller;

import com.sigcon.backend.audit.application.AuditDashboardDTO;
import com.sigcon.backend.audit.application.AuditLogDTO;
import com.sigcon.backend.audit.application.AuditLogFilterRequest;
import com.sigcon.backend.audit.domain.service.AuditExportService;
import com.sigcon.backend.audit.domain.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-AU-01 a HU-AU-09: Endpoints del modulo de Auditoria.
 *
 * <p>Solo lectura y exportacion. La tabla {@code audit_logs} es append-only:
 * los registros NO pueden modificarse ni eliminarse. La creacion ocurre
 * automaticamente via {@code AuditEventListener} (no hay endpoint POST
 * publico para crear logs manualmente).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Modulo de Auditoria",
     description = "Logs inmutables, busqueda avanzada, dashboard y exportacion (HU-AU-01 a 09)")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final AuditExportService exportService;
    // BNK-HU-065: verificacion de integridad de la cadena de hashes.
    private final com.sigcon.backend.audit.domain.service.AuditIntegrityService integrityService;

    @Operation(
        summary = "Listar logs paginados (HU-AU-05 base)",
        description = "Retorna logs paginados ordenados por timestamp DESC. Para busquedas con "
                    + "filtros usar POST /search.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pagina de logs"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/logs")
    public ResponseEntity<?> list(
            @Parameter(description = "Pagina (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamano de pagina (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        var pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLogDTO> result = auditLogService.search(
                new AuditLogFilterRequest(), pageable);
        Map<String, Object> body = new HashMap<>();
        body.put("content", result.getContent());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(body);
    }

    @Operation(
        summary = "Detalle de un evento (HU-AU-08)",
        description = "Retorna metadatos completos + hash chain + valores antes/despues.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Evento encontrado"),
        @ApiResponse(responseCode = "400", description = "Evento inexistente"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/logs/{id}")
    public ResponseEntity<?> getById(
            @Parameter(description = "ID del log", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(auditLogService.findById(id));
    }

    @Operation(
        summary = "Busqueda avanzada con filtros (HU-AU-05)",
        description = "Filtros: usuario, modulo, accion, severidad, entidad, rango de fechas, "
                    + "texto libre en descripcion.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pagina de logs filtrados"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/logs/search")
    public ResponseEntity<?> search(
            @RequestBody AuditLogFilterRequest filter,
            @Parameter(description = "Pagina (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamano de pagina", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        var pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLogDTO> result = auditLogService.search(filter, pageable);
        Map<String, Object> body = new HashMap<>();
        body.put("content", result.getContent());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(body);
    }

    @Operation(
        summary = "Historial de una entidad (HU-AU-09)",
        description = "Lista todos los eventos asociados a una entidad especifica "
                    + "ordenados cronologicamente DESC.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Eventos de la entidad"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/logs/entity/{entityType}/{entityId}")
    public ResponseEntity<?> findByEntity(
            @Parameter(description = "Tipo de entidad", example = "ThirdParty")
            @PathVariable String entityType,
            @Parameter(description = "ID de la entidad", example = "1")
            @PathVariable Long entityId) {
        List<AuditLogDTO> logs = auditLogService.findByEntity(entityType, entityId);
        return ResponseEntity.ok(logs);
    }

    @Operation(
        summary = "Eventos vinculados a un asiento contable (HU-AU-09)",
        description = "Trazabilidad financiera: lista los eventos que generaron o "
                    + "modificaron un asiento contable.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Eventos vinculados al JE"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/logs/journal-entry/{journalEntryId}")
    public ResponseEntity<?> findByJournalEntry(
            @Parameter(description = "ID del asiento contable", example = "1")
            @PathVariable Long journalEntryId) {
        return ResponseEntity.ok(auditLogService.findByJournalEntry(journalEntryId));
    }

    @Operation(
        summary = "Dashboard de auditoria (HU-AU-07)",
        description = "Retorna KPIs: total eventos, conteo por severidad/modulo/accion (ult. 30 dias) "
                    + "y los ultimos 10 eventos.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Datos del dashboard"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/dashboard")
    public ResponseEntity<AuditDashboardDTO> dashboard() {
        return ResponseEntity.ok(auditLogService.getDashboardData());
    }

    @Operation(
        summary = "Ultimos 10 eventos (HU-AU-07)",
        description = "Para mostrar en el feed del dashboard. Ordenados por timestamp DESC.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/logs/latest")
    public ResponseEntity<?> latest() {
        return ResponseEntity.ok(auditLogService.findLatest(10));
    }

    @Operation(
        summary = "Exportar logs (HU-AU-06)",
        description = "Exporta logs en el formato indicado: csv, xlsx o pdf. "
                    + "Si se envia body con filtros, exporta SOLO los resultados filtrados (HU-AU-05 E4).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Archivo generado y descargado"),
        @ApiResponse(responseCode = "204", description = "No hay registros para exportar"),
        @ApiResponse(responseCode = "400", description = "Formato no soportado"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/export/{format}")
    public ResponseEntity<?> exportFiltered(
            @Parameter(description = "Formato (csv | xlsx | pdf)", example = "csv")
            @PathVariable String format,
            @RequestBody(required = false) AuditLogFilterRequest filter) {
        return doExport(format, filter != null ? filter : new AuditLogFilterRequest());
    }

    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/export/{format}")
    public ResponseEntity<?> exportSimple(@PathVariable String format) {
        return doExport(format, new AuditLogFilterRequest());
    }

    private ResponseEntity<?> doExport(String format, AuditLogFilterRequest filter) {
        try {
            var pageable = PageRequest.of(0, 5000, Sort.by(Sort.Direction.DESC, "timestamp"));
            Page<AuditLogDTO> page = auditLogService.search(filter, pageable);
            List<AuditLogDTO> logs = page.getContent();

            // HU-AU-08 E7 (2026-04-28): si no hay datos, devolver mensaje exacto
            // con HTTP 200 (en vez de 204 que descarta el body) para que el
            // frontend reciba el mensaje y lo muestre al usuario.
            if (logs.isEmpty()) {
                Map<String, Object> body = new HashMap<>();
                body.put("success", false);
                body.put("totalElements", 0);
                body.put("message", "No se encontraron registros para los parametros seleccionados");
                // Registra el intento en audit log con conteo=0 (HU-AU-08 E7).
                try {
                    auditLogService.register(
                            com.sigcon.backend.audit.domain.model.enums.AuditAction.EXPORT,
                            com.sigcon.backend.audit.domain.model.enums.AuditModule.AU,
                            null, "AuditLog", null,
                            "Exportacion " + format + " sin resultados (filtros sin coincidencias)",
                            null, null, null);
                } catch (Exception ignored) {}
                return ResponseEntity.ok(body);
            }

            byte[] body;
            MediaType contentType;
            String filename;
            switch (format.toLowerCase()) {
                case "csv":
                    body = exportService.exportToCsv(logs);
                    contentType = MediaType.parseMediaType("text/csv; charset=UTF-8");
                    filename = "audit-logs.csv";
                    break;
                case "xlsx":
                    body = exportService.exportToExcel(logs);
                    contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    filename = "audit-logs.xlsx";
                    break;
                case "pdf":
                    // HU-AU-06 E1 / HU-AU-08 E3 (2026-04-28): PDF real con iText.
                    body = exportService.exportToPdf(logs);
                    contentType = MediaType.APPLICATION_PDF;
                    filename = "audit-logs.pdf";
                    break;
                default:
                    Map<String, Object> err = new HashMap<>();
                    err.put("success", false);
                    err.put("message", "No se pudo generar el reporte en el formato solicitado");
                    return ResponseEntity.badRequest().body(err);
            }

            // HU-AU-06 E6: registrar evento de exportacion con conteo + filtros.
            auditLogService.register(
                    com.sigcon.backend.audit.domain.model.enums.AuditAction.EXPORT,
                    com.sigcon.backend.audit.domain.model.enums.AuditModule.AU,
                    null, "AuditLog", null,
                    "Exportacion de logs en formato " + format + " (" + logs.size() + " registros)",
                    null, null, null);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(contentType)
                    .body(body);
        } catch (Exception e) {
            // HU-AU-06 E5 / HU-AU-08 E8 (2026-04-28): mensaje exacto HU.
            log.error("Error generando export {}: {}", format, e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "No se pudo generar el reporte en el formato solicitado");
            err.put("incidentRef", "AU-EXP-" + System.currentTimeMillis());
            return ResponseEntity.status(500).body(err);
        }
    }

    @Operation(summary = "Exportar dashboard como PDF (HU-AU-07 E6)",
               description = "Genera un PDF con los KPIs y conteos del dashboard, no la lista de logs individuales.")
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/dashboard/export/pdf")
    public ResponseEntity<?> exportDashboardPdf() {
        try {
            var dashboard = auditLogService.getDashboardData();
            byte[] body = exportService.exportDashboardToPdf(dashboard);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-dashboard.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(body);
        } catch (Exception e) {
            log.error("Error generando PDF dashboard", e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "No se pudo generar el reporte en el formato solicitado");
            return ResponseEntity.status(500).body(err);
        }
    }

    @Operation(summary = "Verificar integridad del log de auditoria (BNK-HU-065 E5)",
               description = "Recorre la cadena de hashes y valida encadenamiento + recalculo de contenido. "
                       + "Devuelve OK o RUPTURA con el detalle del primer registro inconsistente.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resultado de la verificacion"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/verify-integrity")
    public ResponseEntity<?> verifyIntegrity() {
        String user = "system";
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) user = auth.getName();
        } catch (Exception ignored) {}
        var r = integrityService.verifyAndRecord("MANUAL", user);
        Map<String, Object> body = new HashMap<>();
        body.put("result", r.result());
        body.put("totalVerified", r.totalVerified());
        body.put("firstBrokenId", r.firstBrokenId());
        body.put("chainBreaks", r.chainBreaks());
        body.put("contentMismatches", r.contentMismatches());
        body.put("durationMs", r.durationMs());
        body.put("detail", r.detail());
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Historial de verificaciones de integridad (BNK-HU-065 E4)")
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/integrity/history")
    public ResponseEntity<?> integrityHistory() {
        return ResponseEntity.ok(integrityService.history());
    }
}
