package com.sigcon.backend.platform.aaef_monitoring.interfaces.controller;

import com.sigcon.backend.platform.aaef_monitoring.domain.service.AaefMonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HU-PA-PLAT-05: monitoreo cross-tenant del modulo AAEF.
 *
 * <p>Solo accesible para PLATFORM_ADMIN. Provee vista agregada de lotes
 * recibidos, latencia por empresa, alertas de cola de reintentos y
 * reintento manual.
 *
 * <ul>
 *   <li>{@code GET /overview?hours=24&companyId=N} — totales por estado en ventana.</li>
 *   <li>{@code GET /batches?hours=24&companyId=&status=&limit=&offset=} — lista metadata estructural.</li>
 *   <li>{@code GET /latency} — latencia promedio por empresa (7 dias).</li>
 *   <li>{@code GET /retry-alerts?hours=1} — count de pendientes mas de N horas.</li>
 *   <li>{@code POST /batches/{id}/retry-ack} — reintento manual ACK.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/platform/aaef")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PLATFORM_ADMIN') or hasAuthority('PERM_PLAT.AAEF.VER')")
@Tag(name = "Plataforma — AAEF Monitoring",
     description = "HU-PA-PLAT-05: monitoreo cross-tenant de la integración AAEF")
public class AaefMonitoringController {

    private final AaefMonitoringService service;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('PERM_INT.MONITOREO.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "HU-PA-PLAT-05 E1+E2: totales por estado y empresa en ventana")
    public ResponseEntity<?> overview(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(required = false) Long companyId) {
        return ResponseEntity.ok(service.getOverview(hours, companyId));
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAuthority('PERM_INT.MONITOREO.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "HU-PA-PLAT-05 E1+E6: lista metadata estructural sin payload")
    public ResponseEntity<?> batches(
            @RequestParam(defaultValue = "168") int hours,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(Map.of(
                "data", service.listBatches(hours, companyId, status, limit, offset),
                "limit", limit,
                "offset", offset));
    }

    @GetMapping("/latency")
    @PreAuthorize("hasAuthority('PERM_INT.MONITOREO.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "HU-PA-PLAT-05 E3: latencia promedio por empresa (7 dias)")
    public ResponseEntity<?> latency() {
        return ResponseEntity.ok(Map.of("data", service.latencyByCompany()));
    }

    @GetMapping("/retry-alerts")
    @PreAuthorize("hasAuthority('PERM_INT.MONITOREO.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "HU-PA-PLAT-05 E4: cantidad de lotes ACK_PENDING con mas de N horas")
    public ResponseEntity<?> alerts(@RequestParam(defaultValue = "1") long hours) {
        return ResponseEntity.ok(service.retryAlerts(hours));
    }

    @PostMapping("/batches/{id}/retry-ack")
    @PreAuthorize("hasAuthority('PERM_INT.MONITOREO.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "HU-PA-PLAT-05 E5: reintento manual de ACK")
    public ResponseEntity<?> retryAck(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.retryAck(id));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(Map.of(
                    "success", false, "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", ex.getMessage()));
        }
    }
}
