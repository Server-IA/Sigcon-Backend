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
}
