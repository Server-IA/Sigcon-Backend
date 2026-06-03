package com.sigcon.backend.audit.interfaces.controller;

import com.sigcon.backend.audit.domain.model.AuditRetentionPolicy;
import com.sigcon.backend.audit.domain.service.RetentionService;
import com.sigcon.backend.utils.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HU-AU-10: Politicas de retencion + legal hold + ejecucion de purga.
 *
 * <p>Endpoints administrativos para gestionar el ciclo de vida de los logs
 * de auditoria: definir cuanto tiempo se conservan, marcar registros con
 * retencion legal indefinida, y ejecutar purga manual o consultar el historial.
 */
@RestController
@RequestMapping("/api/v1/audit/retention")
@RequiredArgsConstructor
@Tag(name = "Auditoria - Retencion y Purga",
     description = "Politicas de retencion, legal hold y purga programada (HU-AU-10)")
public class RetentionController {

    private final RetentionService service;
    private final UserUtil userUtil;

    @Operation(
        summary = "Listar politicas de retencion (HU-AU-10 E1)",
        description = "Retorna todas las politicas configuradas. Cada log nuevo se asigna a "
                    + "la politica mas especifica que matchea (modulo+severidad > severidad sola).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado de politicas"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAuthority('PERM_AU.RETENCION.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/policies")
    public ResponseEntity<?> listPolicies() { return service.listPolicies(); }

    @Operation(
        summary = "Crear politica de retencion (HU-AU-10 E1)",
        description = "Crea politica con: name, description, matchModule (null=todos), "
                    + "matchSeverity (null=todas), retentionDays (>0 obligatorio), "
                    + "legalBasis (norma de respaldo), enabled.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Politica creada"),
        @ApiResponse(responseCode = "400",
                description = "HU-AU-10 E8: 'Error en configuracion de politica de retencion. "
                            + "Operacion cancelada.' (retentionDays invalido)"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAuthority('PERM_AU.RETENCION.CREAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/policies")
    public ResponseEntity<?> createPolicy(@RequestBody AuditRetentionPolicy policy) {
        String createdBy = "admin";
        try { createdBy = userUtil.getUser().getEmail(); } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
     throw __tie;
 } catch (Exception ignored) {}
        return service.createPolicy(policy, createdBy);
    }

    @Operation(
        summary = "Actualizar politica",
        description = "Modifica una politica existente. Las nuevas reglas aplican a logs "
                    + "POSTERIORES (los existentes mantienen su retention_until ya calculado).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Politica actualizada"),
        @ApiResponse(responseCode = "400", description = "Politica no encontrada o configuracion invalida"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAuthority('PERM_AU.RETENCION.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PutMapping("/policies/{id}")
    public ResponseEntity<?> updatePolicy(
            @Parameter(description = "ID de la politica", example = "1") @PathVariable Long id,
            @RequestBody AuditRetentionPolicy policy) {
        return service.updatePolicy(id, policy);
    }

    @Operation(
        summary = "Eliminar politica (soft delete)",
        description = "Desactiva una politica. Los logs ya marcados con su retention_until la conservan.")
    @PreAuthorize("hasAuthority('PERM_AU.RETENCION.ELIMINAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @DeleteMapping("/policies/{id}")
    public ResponseEntity<?> deletePolicy(@PathVariable Long id) {
        return service.deletePolicy(id);
    }

    @Operation(
        summary = "Aplicar legal hold a un log (HU-AU-10 E7)",
        description = "Marca un log con retencion legal activa. NO podra ser purgado aunque "
                    + "su retention_until expire. Usado para evidencias en proceso judicial/auditoria.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Legal hold aplicado"),
        @ApiResponse(responseCode = "400",
                description = "HU-AU-10 E7: 'El registro no puede eliminarse mientras exista "
                            + "retencion legal activa.' o log ya purgado"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAnyAuthority('PERM_LEGAL_HOLD','TEMP_PERM_LEGAL_HOLD','TEMP_LEGAL_HOLD','PERM_AU.RETENCION.LEGAL_HOLD','TEMP_PERM_AU.RETENCION.LEGAL_HOLD','TEMP_AU.RETENCION.LEGAL_HOLD','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @PostMapping("/legal-hold/{logId}")
    public ResponseEntity<?> setLegalHold(
            @Parameter(description = "ID del log", example = "1") @PathVariable Long logId,
            @RequestBody Map<String, String> body) {
        String reason = body.get("reason");
        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Debe indicar la razon del legal hold"));
        }
        return service.setLegalHold(logId, reason);
    }

    @Operation(
        summary = "Liberar legal hold (HU-AU-10 E7)",
        description = "Quita la marca de retencion legal. El log vuelve a estar sujeto a la "
                    + "purga normal cuando expire su retention_until.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Legal hold liberado"),
        @ApiResponse(responseCode = "400", description = "Log no encontrado"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAnyAuthority('PERM_LEGAL_HOLD','TEMP_PERM_LEGAL_HOLD','TEMP_LEGAL_HOLD','PERM_AU.RETENCION.LEGAL_HOLD','TEMP_PERM_AU.RETENCION.LEGAL_HOLD','TEMP_AU.RETENCION.LEGAL_HOLD','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @DeleteMapping("/legal-hold/{logId}")
    public ResponseEntity<?> releaseLegalHold(@PathVariable Long logId) {
        return service.releaseLegalHold(logId);
    }

    @Operation(
        summary = "Legal hold MASIVO (QA Auditoria 2026-06-02)",
        description = "Aplica retencion legal a TODOS los logs que coinciden con los criterios: "
                    + "modulo, severidad y/o rango de fechas (= periodo contable). Ej.: todos los "
                    + "registros del modulo CG, o todo 2026-01. Requiere motivo. Respeta el tenant.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Legal hold masivo aplicado (affected/matched)"),
        @ApiResponse(responseCode = "400", description = "Sin motivo o sin criterios"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyAuthority('PERM_LEGAL_HOLD','TEMP_PERM_LEGAL_HOLD','TEMP_LEGAL_HOLD','PERM_AU.RETENCION.LEGAL_HOLD','TEMP_PERM_AU.RETENCION.LEGAL_HOLD','TEMP_AU.RETENCION.LEGAL_HOLD','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @PostMapping("/legal-hold/bulk")
    public ResponseEntity<?> setLegalHoldBulk(@RequestBody Map<String, String> body) {
        return service.setLegalHoldBulk(
                body.get("module"), body.get("severity"),
                parseDt(body.get("dateFrom")), parseDt(body.get("dateTo")),
                body.get("reason"));
    }

    @Operation(summary = "Liberar legal hold MASIVO por criterios (QA Auditoria 2026-06-02)")
    @PreAuthorize("hasAnyAuthority('PERM_LEGAL_HOLD','TEMP_PERM_LEGAL_HOLD','TEMP_LEGAL_HOLD','PERM_AU.RETENCION.LEGAL_HOLD','TEMP_PERM_AU.RETENCION.LEGAL_HOLD','TEMP_AU.RETENCION.LEGAL_HOLD','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @PostMapping("/legal-hold/bulk/release")
    public ResponseEntity<?> releaseLegalHoldBulk(@RequestBody Map<String, String> body) {
        return service.releaseLegalHoldBulk(
                body.get("module"), body.get("severity"),
                parseDt(body.get("dateFrom")), parseDt(body.get("dateTo")));
    }

    /** Parsea "yyyy-MM-dd" o "yyyy-MM-ddTHH:mm:ss" a LocalDateTime (null si vacio). */
    private static java.time.LocalDateTime parseDt(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String t = s.trim();
            if (t.length() == 10) t = t + "T00:00:00"; // solo fecha
            return java.time.LocalDateTime.parse(t);
        } catch (Exception e) {
            return null;
        }
    }

    @Operation(
        summary = "Ejecutar purga manual (HU-AU-10 E3/E4)",
        description = "Dispara inmediatamente la purga de logs con retencion vencida + sin "
                    + "legal hold + sin archived_at. Genera AuditPurgeRecord con hash SHA-256 "
                    + "del lote (HU-AU-10 E5/E6). Marca logs como archived_at (soft archive) "
                    + "preservando la cadena de hash.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Purga ejecutada (retorna AuditPurgeRecord o 'Sin candidatos')"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAnyAuthority('PERM_EXECUTE_RETENTION_PURGE','TEMP_PERM_EXECUTE_RETENTION_PURGE','TEMP_EXECUTE_RETENTION_PURGE','PERM_AU.RETENCION.EJECUTAR_PURGA','TEMP_PERM_AU.RETENCION.EJECUTAR_PURGA','TEMP_AU.RETENCION.EJECUTAR_PURGA','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @PostMapping("/purge/run")
    public ResponseEntity<?> runPurgeManual() {
        String executedBy = "admin";
        try { executedBy = userUtil.getUser().getEmail(); } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
     throw __tie;
 } catch (Exception ignored) {}
        return service.executePurgeManual(executedBy);
    }

    @Operation(
        summary = "Listar registros de purga (HU-AU-10 E6)",
        description = "Historial de las ultimas 20 ejecuciones de purga con cantidad, rango "
                    + "temporal y batch_hash de evidencia.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Listado"))
    @PreAuthorize("hasAuthority('PERM_AU.RETENCION.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/purge/records")
    public ResponseEntity<?> listPurgeRecords() {
        return service.listPurgeRecords();
    }

    @Operation(
        summary = "Estado del ciclo de vida (HU-AU-10 E10)",
        description = "Resumen: total de logs, cantidad con legal hold activo, politicas activas, "
                    + "purgas recientes.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Estado"))
    @PreAuthorize("hasAuthority('PERM_AU.RETENCION.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return service.getStatus();
    }

    /**
     * HU-AU-10 E6 (2026-04-28): descarga PDF de evidencia de un lote de purga.
     * Incluye metadata + hash SHA-256 del lote para verificacion de integridad.
     */
    @Operation(
        summary = "Descargar PDF de evidencia de purga (HU-AU-10 E6)",
        description = "Genera PDF con metadata del proceso (fecha, usuario, lote) + hash SHA-256 "
                    + "global. NO firmado digitalmente — el hash sirve como evidencia de integridad. "
                    + "Para firma digital con certificado X.509 ver HU-AU-11.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PDF generado"),
        @ApiResponse(responseCode = "404", description = "Registro de purga no encontrado")
    })
    @PreAuthorize("hasAuthority('PERM_AU.RETENCION.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/purge/records/{id}/pdf")
    public ResponseEntity<?> downloadPurgePdf(@org.springframework.web.bind.annotation.PathVariable Long id) {
        try {
            byte[] body = service.exportPurgeRecordPdf(id);
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=audit-purge-" + id + ".pdf")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(java.util.Map.of(
                    "error", e.getMessage(),
                    "message", e.getMessage(),
                    "msg", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of(
                    "error", "No se pudo generar el reporte en el formato solicitado",
                    "message", "No se pudo generar el reporte en el formato solicitado",
                    "msg", "No se pudo generar el reporte en el formato solicitado"));
        }
    }
}
