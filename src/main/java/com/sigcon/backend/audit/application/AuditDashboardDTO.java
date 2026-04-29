package com.sigcon.backend.audit.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Datos del dashboard de auditoria (HU-AU-07)")
public class AuditDashboardDTO {
    @Schema(description = "Total de eventos registrados desde el inicio")
    private long totalEvents;
    @Schema(description = "Conteo por severidad (LOW/MEDIUM/HIGH/CRITICAL) ultimos 30 dias")
    private Map<String, Long> countBySeverity;
    @Schema(description = "Conteo por modulo (PA/TER/AP/...) ultimos 30 dias")
    private Map<String, Long> countByModule;
    @Schema(description = "Conteo por accion (CREATE/UPDATE/DELETE/...) ultimos 30 dias")
    private Map<String, Long> countByAction;
    @Schema(description = "Ultimos 10 eventos")
    private List<AuditLogDTO> latestEvents;

    /**
     * HU-AU-07 E4 (2026-04-28): metricas de cumplimiento normativo NIIF.
     * Calculadas a partir del modulo NiifAlerts del area de Activos.
     */
    @Schema(description = "Total de verificaciones NIIF realizadas")
    private Long niifVerificationsTotal;

    @Schema(description = "Total de alertas NIIF abiertas (severidad WARNING + CRITICAL)")
    private Long niifAlertsOpen;

    @Schema(description = "Porcentaje de cumplimiento NIIF (% verificaciones COMPLIANT vs total)")
    private Double niifCompliancePct;

    /**
     * HU-AU-07 E4 (2026-04-28): metricas de cumplimiento control interno (SOX-like).
     * SIGCON no implementa SOX formal pero exponemos indicador derivado:
     * % de eventos NO criticos sobre el total (mas alto = mejor control).
     */
    @Schema(description = "Porcentaje de control interno (eventos no-CRITICAL / total) ultimos 30 dias")
    private Double soxControlPct;

    @Schema(description = "Eventos criticos pendientes de revision en los ultimos 30 dias")
    private Long criticalEventsRecent;
}
