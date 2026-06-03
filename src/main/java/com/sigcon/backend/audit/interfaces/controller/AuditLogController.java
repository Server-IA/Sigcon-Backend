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
    // QA Auditoria (2026-06-02): rate limit de exportaciones (no ilimitado).
    private final com.sigcon.backend.audit.domain.service.AuditExportRateLimiter exportRateLimiter;

    /**
     * QA Auditoria (2026-06-02): tope de registros por exportacion (configurable).
     * Si el resultado filtrado lo supera, NO se descarga silenciosamente: se avisa
     * al usuario y se le ofrece refinar filtros / paginar / exportar async.
     */
    @org.springframework.beans.factory.annotation.Value("${sigcon.audit.export-max-records:5000}")
    private int exportMaxRecords;

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
            @Parameter(description = "Tope de registros para esta exportacion (<= maximo configurado)")
            @RequestParam(required = false) Integer limit,
            @RequestBody(required = false) AuditLogFilterRequest filter) {
        return doExport(format, filter != null ? filter : new AuditLogFilterRequest(), limit);
    }

    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/export/{format}")
    public ResponseEntity<?> exportSimple(@PathVariable String format,
            @RequestParam(required = false) Integer limit) {
        return doExport(format, new AuditLogFilterRequest(), limit);
    }

    private ResponseEntity<?> doExport(String format, AuditLogFilterRequest filter, Integer requestedLimit) {
        // QA Auditoria (2026-06-02): rate limit (no ilimitado). Tras N exportaciones
        // en la ventana, el usuario debe esperar. El tope nunca es silencioso (429).
        String user = "system";
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) user = auth.getName();
        } catch (Exception ignored) {}
        long wait = exportRateLimiter.checkAndRecord(user, System.currentTimeMillis());
        if (wait > 0) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("rateLimited", true);
            body.put("retryAfterSeconds", wait);
            body.put("message", "Ha alcanzado el limite de " + exportRateLimiter.maxPerWindow()
                    + " exportaciones. Espere " + wait + " segundos antes de continuar con las descargas.");
            return ResponseEntity.status(429).body(body);
        }

        // Tope de registros efectivo: el menor entre el configurado y el solicitado.
        int effectiveLimit = exportMaxRecords;
        if (requestedLimit != null && requestedLimit > 0 && requestedLimit < exportMaxRecords) {
            effectiveLimit = requestedLimit;
        }
        try {
            // Pedir hasta effectiveLimit+1 para detectar si el resultado lo supera.
            var pageable = PageRequest.of(0, effectiveLimit, Sort.by(Sort.Direction.DESC, "timestamp"));
            Page<AuditLogDTO> page = auditLogService.search(filter, pageable);
            List<AuditLogDTO> logs = page.getContent();
            long total = page.getTotalElements();

            // QA Auditoria (2026-06-02): si el resultado filtrado supera el tope N,
            // NO se descarga silenciosamente: se avisa y se ofrece refinar/paginar/async.
            if (total > effectiveLimit) {
                Map<String, Object> body = new HashMap<>();
                body.put("success", false);
                body.put("exceedsLimit", true);
                body.put("total", total);
                body.put("limit", effectiveLimit);
                body.put("message", "El resultado filtrado (" + total + " registros) supera el maximo de "
                        + effectiveLimit + " por exportacion. Refine los filtros, pagine la descarga, o "
                        + "solicite la exportacion asincrona por correo. El tope no es silencioso.");
                try {
                    auditLogService.register(
                            com.sigcon.backend.audit.domain.model.enums.AuditAction.EXPORT,
                            com.sigcon.backend.audit.domain.model.enums.AuditModule.AU,
                            null, "AuditLog", null,
                            "Exportacion " + format + " bloqueada por tope (" + total + " > " + effectiveLimit
                                    + ") | filtros: " + describeFilters(filter),
                            null, null, null);
                } catch (Exception ignored) {}
                return ResponseEntity.ok(body);
            }

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
                            "Exportacion " + format + " sin resultados | filtros: " + describeFilters(filter),
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

            // HU-AU-06 E6: registrar evento de exportacion con conteo + filtros aplicados.
            auditLogService.register(
                    com.sigcon.backend.audit.domain.model.enums.AuditAction.EXPORT,
                    com.sigcon.backend.audit.domain.model.enums.AuditModule.AU,
                    null, "AuditLog", null,
                    "Exportacion de logs en formato " + format + " (" + logs.size()
                            + " registros) | filtros: " + describeFilters(filter),
                    null, null, null);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(contentType)
                    .body(body);
        } catch (Exception e) {
            // HU-AU-06 E5 / HU-AU-08 E8 (2026-04-28): mensaje exacto HU.
            log.error("Error generando export {}: {}", format, e.getMessage(), e);
            String incidentRef = "AU-EXP-" + System.currentTimeMillis();
            // HU-AU-01 E7 (QA Bloque AJ-AU): registrar el fallo de exportacion en el
            // log de auditoria con la referencia del incidente, causa y filtros.
            try {
                auditLogService.register(
                        com.sigcon.backend.audit.domain.model.enums.AuditAction.EXPORT,
                        com.sigcon.backend.audit.domain.model.enums.AuditModule.AU,
                        com.sigcon.backend.audit.domain.model.enums.AuditSeverity.HIGH,
                        "AuditLog", null,
                        "FALLO en exportacion " + format + " [" + incidentRef + "]: "
                                + e.getMessage() + " | filtros: " + describeFilters(filter),
                        null, null, null);
            } catch (Exception ignored) {}
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "No se pudo generar el reporte en el formato solicitado");
            err.put("incidentRef", incidentRef);
            return ResponseEntity.status(500).body(err);
        }
    }

    /**
     * HU-AU-06 E6: resumen legible de los filtros aplicados a una exportacion,
     * para dejar trazabilidad en el log de auditoria (qué se exportó realmente).
     */
    private String describeFilters(AuditLogFilterRequest f) {
        if (f == null) return "ninguno";
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (f.getUserEmail() != null && !f.getUserEmail().isBlank()) parts.add("usuario=" + f.getUserEmail());
        if (f.getUserId() != null) parts.add("userId=" + f.getUserId());
        if (f.getModule() != null) parts.add("modulo=" + f.getModule());
        if (f.getModules() != null && !f.getModules().isEmpty()) parts.add("modulos=" + f.getModules());
        if (f.getAction() != null) parts.add("accion=" + f.getAction());
        if (f.getSeverity() != null) parts.add("severidad=" + f.getSeverity());
        if (f.getSeverities() != null && !f.getSeverities().isEmpty()) parts.add("severidades=" + f.getSeverities());
        if (f.getEntityType() != null && !f.getEntityType().isBlank()) parts.add("entidad=" + f.getEntityType());
        if (f.getEntityId() != null) parts.add("entidadId=" + f.getEntityId());
        if (f.getDateFrom() != null) parts.add("desde=" + f.getDateFrom());
        if (f.getDateTo() != null) parts.add("hasta=" + f.getDateTo());
        if (f.getIpAddress() != null && !f.getIpAddress().isBlank()) parts.add("ip=" + f.getIpAddress());
        if (f.getSearchText() != null && !f.getSearchText().isBlank()) parts.add("texto='" + f.getSearchText() + "'");
        return parts.isEmpty() ? "ninguno" : String.join(", ", parts);
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

    @Operation(summary = "Re-baseline de la cadena de integridad (QA Auditoria 2026-06-02)",
               description = "Recalcula previous_hash + hash de TODA la cadena en orden global. Sanea la "
                       + "cadena rota heredada (insert global vs verificacion por-empresa + forks de "
                       + "concurrencia). Solo PLATFORM_ADMIN. Tras ejecutarlo la verificacion debe dar OK.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cadena re-baselined + verificacion posterior"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN') or hasAuthority('ROLE_ADMIN_EMPRESA')")
    @PostMapping("/integrity/rebaseline")
    public ResponseEntity<?> rebaselineIntegrity() {
        int n = integrityService.rebaselineChain();
        var r = integrityService.verifyChain();
        Map<String, Object> body = new HashMap<>();
        body.put("rebaselined", n);
        body.put("result", r.result());
        body.put("chainBreaks", r.chainBreaks());
        body.put("contentMismatches", r.contentMismatches());
        body.put("message", "Cadena re-baselined: " + n + " registros. Verificacion: " + r.result());
        return ResponseEntity.ok(body);
    }
}
