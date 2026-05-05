package com.sigcon.backend.platform.audit.interfaces.controller;

import com.sigcon.backend.platform.audit.application.PlatformAuditLogDTO;
import com.sigcon.backend.platform.audit.domain.service.PlatformAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * HU-PA-PLAT-08 — endpoints del log de auditoria de plataforma.
 *
 * Solo accesible por PLATFORM_ADMIN.
 */
@RestController
@RequestMapping("/api/platform/audit-log")
@RequiredArgsConstructor
@Tag(name = "Plataforma — Audit log", description = "HU-PA-PLAT-08: log inmutable de acciones cross-tenant")
public class PlatformAuditController {

    private final PlatformAuditService service;

    @GetMapping
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN') or hasAuthority('PERM_PLAT.AUDIT_LOG.VER')")
    @Operation(summary = "Listado paginado del log de plataforma con filtros")
    public ResponseEntity<?> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<PlatformAuditLogDTO> data = service.search(from, to, action, actor, targetType, targetId, page, size)
                .map(PlatformAuditLogDTO::from);
        Map<String, Object> body = new HashMap<>();
        body.put("data", data.getContent());
        body.put("totalElements", data.getTotalElements());
        body.put("totalPages", data.getTotalPages());
        body.put("page", page);
        body.put("size", size);
        return ResponseEntity.ok(body);
    }

    /**
     * HU-PA-PLAT-08 E3: exportacion a CSV. La accion misma queda registrada
     * en el log con action=PLATFORM_AUDIT_EXPORTED.
     */
    @GetMapping("/export.csv")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN') or hasAuthority('PERM_PLAT.AUDIT_LOG.EXPORTAR')")
    @Operation(summary = "Exportar el log filtrado a CSV (registra PLATFORM_AUDIT_EXPORTED)")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor) {
        Page<PlatformAuditLogDTO> data = service.search(from, to, action, actor, null, null, 0, 10000)
                .map(PlatformAuditLogDTO::from);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (OutputStreamWriter w = new OutputStreamWriter(buf, StandardCharsets.UTF_8)) {
            w.write("﻿"); // BOM UTF-8 para Excel
            w.write("id;occurredAt;actor;action;targetType;targetId;targetLabel;remoteIp\n");
            for (PlatformAuditLogDTO r : data.getContent()) {
                w.write(safeCsv(String.valueOf(r.getId())) + ";");
                w.write(safeCsv(String.valueOf(r.getOccurredAt())) + ";");
                w.write(safeCsv(String.valueOf(r.getActorEmail())) + ";");
                w.write(safeCsv(r.getAction()) + ";");
                w.write(safeCsv(r.getTargetType()) + ";");
                w.write(safeCsv(r.getTargetId()) + ";");
                w.write(safeCsv(r.getTargetLabel()) + ";");
                w.write(safeCsv(r.getRemoteIp()) + "\n");
            }
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(("Error: " + ex.getMessage()).getBytes(StandardCharsets.UTF_8));
        }

        // E3: registrar la exportacion
        service.log("PLATFORM_AUDIT_EXPORTED", "AuditLog", "csv",
                "rango " + from + " a " + to,
                PlatformAuditService.payload("rows", data.getContent().size(),
                        "action", action == null ? "*" : action),
                null);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "platform-audit.csv");
        return new ResponseEntity<>(buf.toByteArray(), headers, 200);
    }

    private String safeCsv(String s) {
        if (s == null) return "";
        return s.replace(';', ',').replace('\n', ' ').replace('\r', ' ');
    }
}
