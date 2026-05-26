package com.sigcon.backend.audit.backup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-AU-09 — Endpoints administrativos para la transferencia de logs de auditoria
 * a la BD externa de respaldo.
 *
 * <ul>
 *   <li>{@code POST /api/v1/audit/backup/transferir} — dispara la transferencia
 *       manualmente (independiente del scheduler).</li>
 *   <li>{@code GET /api/v1/audit/backup/estado} — estado del cursor + pendientes
 *       (lectura LOCAL, no toca la BD externa).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/audit/backup")
@RequiredArgsConstructor
@Tag(name = "2. Auditoria - Respaldo externo (HU-AU-09)",
        description = "Transferencia de logs de auditoria a la BD externa de respaldo con verificacion SHA-256")
public class AuditBackupController {

    private final AuditBackupTransferService service;
    private final JdbcTemplate jdbc;

    @PostMapping("/transferir")
    @PreAuthorize("hasAnyAuthority('PLATFORM_ADMIN','ROLE_ADMIN','ROLE_ADMIN_EMPRESA')")
    @Operation(summary = "Transferir logs pendientes a la BD externa (manual)",
            description = "HU-AU-09 E1: serializa los logs no respaldados, calcula SHA-256 en origen, "
                    + "los inserta en la BD externa, re-verifica el hash en destino (E2) y avanza el cursor "
                    + "solo si el lote queda INTEGRO. Reintenta con backoff exponencial ante fallos (E3/E4).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen de la transferencia"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos de administrador")
    })
    public ResponseEntity<?> transferir() {
        return ResponseEntity.ok(service.transferir("MANUAL"));
    }

    @GetMapping("/estado")
    @PreAuthorize("hasAnyAuthority('PLATFORM_ADMIN','ROLE_ADMIN','ROLE_ADMIN_EMPRESA')")
    @Operation(summary = "Estado del respaldo de auditoria",
            description = "Devuelve el cursor de transferencia (ultimo id respaldado, ultimo estado/mensaje) "
                    + "y cuantos logs quedan pendientes. Lectura LOCAL: no consulta la BD externa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado del cursor + pendientes"),
            @ApiResponse(responseCode = "403", description = "Sin permisos de administrador")
    })
    public ResponseEntity<?> estado() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> cur = jdbc.queryForList(
                "SELECT last_transferred_id, last_run_at, last_status, last_message, updated_at "
                + "FROM audit_backup_cursor WHERE id = 1");
        Map<String, Object> cursor = cur.isEmpty() ? Map.of("last_transferred_id", 0) : cur.get(0);
        long lastId = ((Number) cursor.getOrDefault("last_transferred_id", 0L)).longValue();
        Long pendientes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE id > ?", Long.class, lastId);
        out.put("cursor", cursor);
        out.put("pendientes", pendientes);
        return ResponseEntity.ok(out);
    }
}
