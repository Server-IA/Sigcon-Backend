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

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_PAR.PERMISOS_TEMPORALES.ASIGNAR') or hasAuthority('ROLE_ADMIN_EMPRESA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
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

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('PERM_PAR.PERMISOS_TEMPORALES.REVOCAR') or hasAuthority('ROLE_ADMIN_EMPRESA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
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
    @PreAuthorize("hasAuthority('PERM_PAR.PERMISOS_TEMPORALES.VER') or hasAuthority('ROLE_ADMIN_EMPRESA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
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
    @PreAuthorize("hasAuthority('PERM_PAR.PERMISOS_TEMPORALES.VER') or hasAuthority('ROLE_ADMIN_EMPRESA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "HU-PA-16 E6: detalle con timeline")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        try {
            TemporaryPermission tp = service.findById(id);
            return ResponseEntity.ok(TemporaryPermissionDTO.from(tp));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @GetMapping("/export.csv")
    @PreAuthorize("hasAuthority('PERM_PAR.PERMISOS_TEMPORALES.VER') or hasAuthority('ROLE_ADMIN_EMPRESA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
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
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"temporary-permissions.csv\"")
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
