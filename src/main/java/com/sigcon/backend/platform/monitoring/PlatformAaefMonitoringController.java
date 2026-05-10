package com.sigcon.backend.platform.monitoring;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-PA-PLAT-05: monitoreo AAEF cross-tenant para PLATFORM_ADMIN.
 *
 * <p>QA Bloque PA Bug 67 (2026-05-09).
 *
 * <p>Solo expone metadatos (status, latencia, conteos). NO expone contenido del
 * payload AAEF (HU-PA-PLAT-05 E6).
 */
@RestController
@RequestMapping("/api/platform/aaef-monitor")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
@Tag(name = "Plataforma - Monitoreo AAEF",
     description = "HU-PA-PLAT-05: monitoreo cross-tenant de la integracion con AgroFusion")
public class PlatformAaefMonitoringController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * HU-PA-PLAT-05 E1+E2: distribucion de lotes por status en ventanas de tiempo
     * (24h, 48h, 72h, 7d) con filtro opcional por empresa.
     */
    @GetMapping("/overview")
    @Operation(summary = "HU-PA-PLAT-05 E1+E2: distribucion de lotes por status y ventana")
    public ResponseEntity<?> overview(@RequestParam(required = false) Long companyId) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int hours : new int[]{24, 48, 72, 168}) {
            body.put(hours + "h", statusBreakdown(hours, companyId));
        }
        return ResponseEntity.ok(body);
    }

    /**
     * HU-PA-PLAT-05 E3: latencia promedio por empresa (recepcion -> ack_sent_at).
     */
    @GetMapping("/latency-by-company")
    @Operation(summary = "HU-PA-PLAT-05 E3: latencia promedio por empresa en ultimos 7 dias")
    public ResponseEntity<?> latencyByCompany() {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            jdbcTemplate.query(
                "SELECT b.company_id, c.business_name, COUNT(b.id) AS total, " +
                "  AVG(EXTRACT(EPOCH FROM (b.ack_sent_at - b.received_at))) AS avg_latency_seconds " +
                "FROM integration_batches b LEFT JOIN companies c ON c.id = b.company_id " +
                "WHERE b.received_at > NOW() - INTERVAL '7 days' AND b.deleted_at IS NULL " +
                "  AND b.ack_sent_at IS NOT NULL " +
                "GROUP BY b.company_id, c.business_name " +
                "ORDER BY avg_latency_seconds DESC",
                rs -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("companyId", rs.getLong(1));
                    r.put("companyName", rs.getString(2));
                    r.put("totalBatches", rs.getLong(3));
                    Object avg = rs.getObject(4);
                    r.put("avgLatencySeconds", avg);
                    rows.add(r);
                });
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("data", rows, "error", ex.getMessage()));
        }
        return ResponseEntity.ok(Map.of("data", rows, "windowDays", 7));
    }

    /**
     * HU-PA-PLAT-05 E4: alertas de cola de reintentos (lotes en estado pending de
     * confirmacion con mas de 1 hora de antiguedad).
     */
    @GetMapping("/pending-alerts")
    @Operation(summary = "HU-PA-PLAT-05 E4: lotes con confirmacion pendiente >1h")
    public ResponseEntity<?> pendingAlerts() {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            jdbcTemplate.query(
                "SELECT b.id, b.company_id, c.business_name, b.exchange_id, b.status, " +
                "  b.received_at, b.ack_retry_count " +
                "FROM integration_batches b LEFT JOIN companies c ON c.id = b.company_id " +
                "WHERE b.status IN ('ACK_PENDING','ACK_FAILED') " +
                "  AND b.received_at < NOW() - INTERVAL '1 hour' " +
                "  AND b.deleted_at IS NULL " +
                "ORDER BY b.received_at ASC",
                rs -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("batchId", rs.getLong(1));
                    r.put("companyId", rs.getLong(2));
                    r.put("companyName", rs.getString(3));
                    r.put("exchangeId", rs.getString(4));
                    r.put("status", rs.getString(5));
                    java.sql.Timestamp ts = rs.getTimestamp(6);
                    r.put("receivedAt", ts != null ? ts.toLocalDateTime() : null);
                    r.put("ackRetryCount", rs.getInt(7));
                    rows.add(r);
                });
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("data", rows, "error", ex.getMessage()));
        }
        return ResponseEntity.ok(Map.of(
            "data", rows,
            "alertCount", rows.size(),
            "alertMessage", rows.isEmpty()
                ? "Sin alertas: no hay lotes pendientes >1h"
                : "Hay " + rows.size() + " lote(s) con confirmaciones fallidas a AgroFusion con mas de 1 hora pendientes"
        ));
    }

    private Map<String, Object> statusBreakdown(int hours, Long companyId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        try {
            String sql = "SELECT status, COUNT(*) FROM integration_batches " +
                "WHERE received_at > NOW() - INTERVAL '" + hours + " hours' " +
                "  AND deleted_at IS NULL " +
                (companyId != null ? "AND company_id = ? " : "") +
                "GROUP BY status";
            Object[] args = (companyId != null) ? new Object[]{companyId} : new Object[0];
            jdbcTemplate.query(sql, args, rs -> {
                counts.put(rs.getString(1), rs.getLong(2));
            });
        } catch (Exception ignored) {}
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        result.put("total", total);
        result.put("byStatus", counts);
        result.put("companyFilter", companyId);
        return result;
    }
}
