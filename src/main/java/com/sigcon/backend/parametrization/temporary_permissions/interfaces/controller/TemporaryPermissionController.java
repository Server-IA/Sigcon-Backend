package com.sigcon.backend.parametrization.temporary_permissions.interfaces.controller;

import com.sigcon.backend.parametrization.temporary_permissions.application.TemporaryPermissionDTO;
import com.sigcon.backend.parametrization.temporary_permissions.domain.model.TemporaryPermission;
import com.sigcon.backend.parametrization.temporary_permissions.domain.model.TemporaryPermission.Status;
import com.sigcon.backend.parametrization.temporary_permissions.domain.service.TemporaryPermissionExpiryScheduler;
import com.sigcon.backend.parametrization.temporary_permissions.domain.service.TemporaryPermissionService;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-PA-13 a HU-PA-17 — endpoints de permisos temporales.
 *
 * <ul>
 *   <li>POST /api/parametrization/temporary-permissions — HU-PA-13 (asignar)</li>
 *   <li>POST /api/parametrization/temporary-permissions/{id}/revoke — HU-PA-14</li>
 *   <li>GET /api/parametrization/temporary-permissions — HU-PA-16 (listado/filtros)</li>
 *   <li>GET /api/parametrization/temporary-permissions/me — HU-PA-17 (mis activos)</li>
 *   <li>GET /api/parametrization/temporary-permissions/{id} — HU-PA-16 E6 (detalle)</li>
 *   <li>GET /api/parametrization/temporary-permissions/export.csv — HU-PA-16 E5</li>
 *   <li>POST /api/parametrization/temporary-permissions/run-expiry-now — HU-PA-15 E5 (admin)</li>
 *   <li>GET /api/parametrization/temporary-permissions/scheduler-status — HU-PA-15 E5</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/parametrization/temporary-permissions")
@RequiredArgsConstructor
@Tag(name = "Parametrizacion - Permisos Temporales",
     description = "HU-PA-13 a HU-PA-17: permisos atomicos temporales con vigencia y auditoria")
public class TemporaryPermissionController {

    private final TemporaryPermissionService service;
    private final TemporaryPermissionExpiryScheduler scheduler;
    private final UserRepository userRepository;
    /** QA Bloque PA Bug 45 (HU-PA-16 E5): audit log para registrar exportaciones. */
    private final com.sigcon.backend.audit.domain.service.AuditPublisher auditPublisher;

    /**
     * QA Bloque AV (HU-PA-13 E7 regla #11, 2026-05-14): la accion de ASIGNAR
     * permisos temporales solo se habilita con permiso de ROL (no temporal),
     * para evitar escalada recursiva de privilegios. Por eso NO incluimos el
     * prefijo {@code TEMP_*} en este @PreAuthorize. Si un admin delega
     * temporalmente la facultad de "asignar", el delegado NO puede usarla
     * para asignar a su vez (defensa en profundidad).
     */
    @PostMapping
    @PreAuthorize("hasAuthority('PERM_PAR.PERMISOS_TEMPORALES.ASIGNAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "HU-PA-13 E1: asignar permisos temporales a un usuario")
    public ResponseEntity<?> grant(@RequestBody Map<String, Object> body) {
        try {
            Long userId = asLong(body.get("userId"));
            List<Long> permissionIds = asLongList(body.get("permissionIds"));
            String justification = (String) body.get("justification");
            LocalDateTime startDate = parseDateTime(body.get("startDate"));
            LocalDateTime endDate = parseDateTime(body.get("endDate"));
            List<Long> ids = service.grant(userId, permissionIds, justification, startDate, endDate);
            return ResponseEntity.status(201).body(Map.of(
                    "success", true,
                    "message", "Permisos temporales asignados",
                    "createdIds", ids));
        } catch (TemporaryPermissionService.MaxActiveReachedException ex) {
            return ResponseEntity.status(409).body(Map.of(
                    "success", false, "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", ex.getMessage()));
        }
    }

    /**
     * QA Bloque AV (HU-PA-13 E7 regla #11, 2026-05-14): revocar requiere ROL.
     * Misma razon que ASIGNAR: un delegado temporal NO puede revocar permisos
     * de otros usuarios para evitar manipulacion descentralizada del set
     * de permisos efectivos.
     */
    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('PERM_PAR.PERMISOS_TEMPORALES.REVOCAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "HU-PA-14 E1: revocar permiso temporal con justificacion")
    public ResponseEntity<?> revoke(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            String reason = (String) body.get("reason");
            service.revoke(id, reason);
            return ResponseEntity.ok(Map.of("success", true, "message", "Permiso temporal revocado"));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(Map.of("success", false, "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_PAR.PERMISOS_TEMPORALES.VER','TEMP_PERM_PAR.PERMISOS_TEMPORALES.VER','TEMP_PAR.PERMISOS_TEMPORALES.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "HU-PA-16: listar permisos temporales con filtros")
    public ResponseEntity<?> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Status st = parseStatus(status);
        var pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 200),
                Sort.by("createdAt").descending());
        var result = service.search(userId, st, from, to, pageable);
        return ResponseEntity.ok(Map.of(
                "data", result.getContent().stream().map(TemporaryPermissionDTO::from).toList(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", page, "size", size));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "HU-PA-17 E1+E4: permisos temporales activos del usuario actual")
    public ResponseEntity<?> myActive() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "No autenticado"));
        }
        String username = a.getName();
        User u = userRepository.findByUsernameOrEmail(username, username).orElse(null);
        if (u == null) return ResponseEntity.status(401).body(Map.of("success", false, "message", "Usuario no encontrado"));
        var list = service.getMyActive(u.getId()).stream().map(TemporaryPermissionDTO::from).toList();
        return ResponseEntity.ok(Map.of("data", list));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_PAR.PERMISOS_TEMPORALES.VER','TEMP_PERM_PAR.PERMISOS_TEMPORALES.VER','TEMP_PAR.PERMISOS_TEMPORALES.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "HU-PA-16 E6: detalle con timeline completo de eventos")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        try {
            TemporaryPermission tp = service.findById(id);
            // QA Bloque PA Bug 45 (HU-PA-16 E6, 2026-05-09): incluir array timeline[]
            // con los eventos cronologicos para evidencia de auditoria.
            Map<String, Object> body = new HashMap<>();
            TemporaryPermissionDTO dto = TemporaryPermissionDTO.from(tp);
            body.put("id", dto.getId());
            body.put("companyId", dto.getCompanyId());
            body.put("userId", dto.getUserId());
            body.put("permissionId", dto.getPermissionId());
            body.put("permissionCode", dto.getPermissionCode());
            body.put("grantedByUserId", dto.getGrantedByUserId());
            body.put("grantedByEmail", dto.getGrantedByEmail());
            body.put("justification", dto.getJustification());
            body.put("startDate", dto.getStartDate());
            body.put("endDate", dto.getEndDate());
            body.put("status", dto.getStatus());
            body.put("revokedAt", dto.getRevokedAt());
            body.put("revokedByEmail", dto.getRevokedByEmail());
            body.put("revocationReason", dto.getRevocationReason());
            body.put("createdAt", dto.getCreatedAt());
            body.put("daysRemaining", dto.getDaysRemaining());
            body.put("scheduled", dto.getScheduled());
            body.put("timeline", service.getTimeline(id));
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @GetMapping("/export.csv")
    @PreAuthorize("hasAnyAuthority('PERM_PAR.PERMISOS_TEMPORALES.VER','TEMP_PERM_PAR.PERMISOS_TEMPORALES.VER','TEMP_PAR.PERMISOS_TEMPORALES.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "HU-PA-16 E5: exportar historial a CSV")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        Status st = parseStatus(status);
        var all = service.search(userId, st, from, to,
                PageRequest.of(0, 10000, Sort.by("createdAt").descending()));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (var w = new OutputStreamWriter(buf, StandardCharsets.UTF_8)) {
            w.write("﻿");
            w.write("id;user_id;permission_code;status;start_date;end_date;granted_by;justification;revoked_by;revoked_at;revocation_reason\n");
            for (var t : all.getContent()) {
                w.write(safe(String.valueOf(t.getId())) + ";");
                w.write(safe(String.valueOf(t.getUserId())) + ";");
                w.write(safe(t.getPermissionCode()) + ";");
                w.write(safe(t.getStatus() != null ? t.getStatus().name() : "") + ";");
                w.write(safe(String.valueOf(t.getStartDate())) + ";");
                w.write(safe(String.valueOf(t.getEndDate())) + ";");
                w.write(safe(t.getGrantedByEmail()) + ";");
                w.write(safe(t.getJustification()) + ";");
                w.write(safe(t.getRevokedByEmail()) + ";");
                w.write(safe(String.valueOf(t.getRevokedAt())) + ";");
                w.write(safe(t.getRevocationReason()) + "\n");
            }
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
        // QA Bloque PA Bug 45 (HU-PA-16 E5, 2026-05-09): registrar export en audit log.
        try {
            auditPublisher.publish(
                com.sigcon.backend.audit.domain.model.enums.AuditAction.EXPORT,
                com.sigcon.backend.audit.domain.model.enums.AuditModule.PA,
                com.sigcon.backend.audit.domain.model.enums.AuditSeverity.LOW,
                "TemporaryPermission", 0L,
                "TEMP_PERMISSION_HISTORY_EXPORTED format=CSV count=" + all.getTotalElements()
                    + " filters: userId=" + userId + " status=" + st + " from=" + from + " to=" + to,
                null, null, null);
        } catch (Exception ignored) { /* audit no debe romper export */ }
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"temporary-permissions.csv\"")
                .body(buf.toByteArray());
    }

    /**
     * QA Bloque PA Bug 45 (HU-PA-16 E5, 2026-05-09): exportar historial a XLSX
     * (formato pedido por la HU). Usa Apache POI. Registra en audit log.
     */
    @GetMapping("/export.xlsx")
    @PreAuthorize("hasAnyAuthority('PERM_PAR.PERMISOS_TEMPORALES.VER','TEMP_PERM_PAR.PERMISOS_TEMPORALES.VER','TEMP_PAR.PERMISOS_TEMPORALES.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "HU-PA-16 E5: exportar historial a XLSX (Apache POI)")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        Status st = parseStatus(status);
        var all = service.search(userId, st, from, to,
                PageRequest.of(0, 10000, Sort.by("createdAt").descending()));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Permisos Temporales");
            String[] headers = {"ID","UserID","Permiso","Estado","Inicio","Fin","Otorgado por","Justificacion","Revocado por","Revocado el","Motivo revocacion"};
            org.apache.poi.ss.usermodel.Row hr = sheet.createRow(0);
            org.apache.poi.ss.usermodel.CellStyle bold = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font fb = wb.createFont(); fb.setBold(true); bold.setFont(fb);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell c = hr.createCell(i);
                c.setCellValue(headers[i]); c.setCellStyle(bold);
            }
            int rownum = 1;
            for (var t : all.getContent()) {
                org.apache.poi.ss.usermodel.Row r = sheet.createRow(rownum++);
                r.createCell(0).setCellValue(t.getId() != null ? t.getId() : 0);
                r.createCell(1).setCellValue(t.getUserId() != null ? t.getUserId() : 0);
                r.createCell(2).setCellValue(t.getPermissionCode() != null ? t.getPermissionCode() : "");
                r.createCell(3).setCellValue(t.getStatus() != null ? t.getStatus().name() : "");
                r.createCell(4).setCellValue(String.valueOf(t.getStartDate()));
                r.createCell(5).setCellValue(String.valueOf(t.getEndDate()));
                r.createCell(6).setCellValue(t.getGrantedByEmail() != null ? t.getGrantedByEmail() : "");
                r.createCell(7).setCellValue(t.getJustification() != null ? t.getJustification() : "");
                r.createCell(8).setCellValue(t.getRevokedByEmail() != null ? t.getRevokedByEmail() : "");
                r.createCell(9).setCellValue(String.valueOf(t.getRevokedAt()));
                r.createCell(10).setCellValue(t.getRevocationReason() != null ? t.getRevocationReason() : "");
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            wb.write(buf);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
        try {
            auditPublisher.publish(
                com.sigcon.backend.audit.domain.model.enums.AuditAction.EXPORT,
                com.sigcon.backend.audit.domain.model.enums.AuditModule.PA,
                com.sigcon.backend.audit.domain.model.enums.AuditSeverity.LOW,
                "TemporaryPermission", 0L,
                "TEMP_PERMISSION_HISTORY_EXPORTED format=XLSX count=" + all.getTotalElements()
                    + " filters: userId=" + userId + " status=" + st + " from=" + from + " to=" + to,
                null, null, null);
        } catch (Exception ignored) { /* audit no debe romper */ }
        return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header("Content-Disposition", "attachment; filename=\"temporary-permissions.xlsx\"")
                .body(buf.toByteArray());
    }

    @PostMapping("/run-expiry-now")
    @PreAuthorize("hasAuthority('ROLE_ADMIN_EMPRESA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN') or hasAuthority('PLATFORM_ADMIN')")
    @Operation(summary = "HU-PA-15 E5: re-ejecutar manualmente el job de vencimiento")
    public ResponseEntity<?> runNow() {
        var summary = scheduler.runNow();
        Map<String, Object> out = new HashMap<>();
        out.put("status", summary.status);
        out.put("expiredCount", summary.expiredCount);
        out.put("notifiedCount", summary.notifiedCount);
        out.put("durationMs", summary.durationMs);
        out.put("startedAt", summary.startedAt);
        out.put("endedAt", summary.endedAt);
        if (summary.errorMessage != null) out.put("errorMessage", summary.errorMessage);
        return ResponseEntity.ok(out);
    }

    @GetMapping("/scheduler-status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN_EMPRESA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN') or hasAuthority('PLATFORM_ADMIN')")
    @Operation(summary = "HU-PA-15 E5: estado del ultimo run del scheduler")
    public ResponseEntity<?> schedulerStatus() {
        var summary = scheduler.getLastRun();
        if (summary == null) {
            return ResponseEntity.ok(Map.of("status", "NEVER_RAN"));
        }
        Map<String, Object> out = new HashMap<>();
        out.put("status", summary.status);
        out.put("expiredCount", summary.expiredCount);
        out.put("notifiedCount", summary.notifiedCount);
        out.put("durationMs", summary.durationMs);
        out.put("startedAt", summary.startedAt);
        out.put("endedAt", summary.endedAt);
        if (summary.errorMessage != null) out.put("errorMessage", summary.errorMessage);
        return ResponseEntity.ok(out);
    }

    // -------- helpers --------
    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static List<Long> asLongList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> raw) {
            return raw.stream().map(o -> {
                if (o instanceof Number n) return n.longValue();
                try { return Long.parseLong(String.valueOf(o)); } catch (NumberFormatException e) { return null; }
            }).filter(java.util.Objects::nonNull).toList();
        }
        return List.of();
    }

    private static LocalDateTime parseDateTime(Object v) {
        if (v == null) return null;
        try { return LocalDateTime.parse(String.valueOf(v)); }
        catch (Exception e) { return null; }
    }

    private static Status parseStatus(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Status.valueOf(s.toUpperCase()); } catch (IllegalArgumentException e) { return null; }
    }

    private static Long currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof User u) return u.getId();
        return null;
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace(';', ',').replace('\n', ' ').replace('\r', ' ');
    }
}
