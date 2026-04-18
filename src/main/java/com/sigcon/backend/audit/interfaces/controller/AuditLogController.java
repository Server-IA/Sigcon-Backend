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

    @Operation(
        summary = "Listar logs paginados (HU-AU-05 base)",
        description = "Retorna logs paginados ordenados por timestamp DESC. Para busquedas con "
                    + "filtros usar POST /search.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pagina de logs"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_AUDIT') or hasAuthority('ROLE_ADMIN')")
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
    @PreAuthorize("hasAuthority('PERM_VIEW_AUDIT') or hasAuthority('ROLE_ADMIN')")
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
    @PreAuthorize("hasAuthority('PERM_VIEW_AUDIT') or hasAuthority('ROLE_ADMIN')")
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
    @PreAuthorize("hasAuthority('PERM_VIEW_AUDIT') or hasAuthority('ROLE_ADMIN')")
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
    @PreAuthorize("hasAuthority('PERM_VIEW_AUDIT') or hasAuthority('ROLE_ADMIN')")
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
    @PreAuthorize("hasAuthority('PERM_VIEW_AUDIT') or hasAuthority('ROLE_ADMIN')")
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
    @PreAuthorize("hasAuthority('PERM_VIEW_AUDIT') or hasAuthority('ROLE_ADMIN')")
    @GetMapping("/logs/latest")
    public ResponseEntity<?> latest() {
        return ResponseEntity.ok(auditLogService.findLatest(10));
    }

    @Operation(
        summary = "Exportar logs (HU-AU-06)",
        description = "Exporta los logs en el formato indicado: csv, xlsx o pdf. "
                    + "Acepta los mismos filtros que /logs/search via query params.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Archivo generado y descargado"),
        @ApiResponse(responseCode = "400", description = "Formato no soportado"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_AUDIT') or hasAuthority('ROLE_ADMIN')")
    @GetMapping("/export/{format}")
    public ResponseEntity<byte[]> export(
            @Parameter(description = "Formato (csv | xlsx | pdf)", example = "csv")
            @PathVariable String format) {
        // Para simplicidad: exporta los ultimos 1000 registros sin filtros adicionales.
        // Para filtros usar POST /logs/search y luego un endpoint de export por ids.
        var pageable = PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLogDTO> page = auditLogService.search(new AuditLogFilterRequest(), pageable);
        List<AuditLogDTO> logs = page.getContent();

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
                body = exportService.exportToPdf(logs);
                contentType = MediaType.parseMediaType("text/plain; charset=UTF-8");
                filename = "audit-logs.txt";
                break;
            default:
                return ResponseEntity.badRequest().body(("Formato no soportado: " + format).getBytes());
        }

        // HU-AU-06 E6: registrar evento de exportacion
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
    }
}
