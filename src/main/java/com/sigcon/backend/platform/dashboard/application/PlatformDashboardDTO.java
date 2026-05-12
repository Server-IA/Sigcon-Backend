package com.sigcon.backend.platform.dashboard.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respuesta del dashboard de plataforma (HU-PA-PLAT-06).
 *
 * <p>KPIs agregados cross-empresa para {@code PLATFORM_ADMIN}. Los conteos
 * se computan con queries nativas que bypasean el tenant filter (el servicio
 * se marca con {@code TenantContext.setPlatformAdmin(true)} durante el calculo).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "KPIs globales de la plataforma SIGCON")
public class PlatformDashboardDTO {

    @Schema(description = "Total de empresas en estado ACTIVE", example = "5")
    private long activeCompanies;

    @Schema(description = "Total de empresas en estado INACTIVE", example = "1")
    private long inactiveCompanies;

    @Schema(description = "Total de usuarios tenant (excluye PLATFORM_ADMIN)", example = "23")
    private long totalTenantUsers;

    @Schema(description = "Total de usuarios PLATFORM_ADMIN", example = "1")
    private long totalPlatformAdmins;

    @Schema(description = "Total de asientos contables en los ultimos 6 meses cross-empresa",
            example = "340")
    private long journalEntriesLast6Months;

    @Schema(description = "Total de lotes AAEF recibidos cross-empresa", example = "18")
    private long totalAaefBatches;

    @Schema(description = "Total de lotes AAEF en estado ACK_FAILED (requieren atencion)",
            example = "2")
    private long ackFailedBatches;

    @Schema(description = "Top 5 empresas por numero de asientos contables",
            example = "[{\"companyId\":2,\"companyName\":\"ACME SAS\",\"journalEntryCount\":85}]")
    private List<CompanyStat> topCompaniesByJe;

    @Schema(description = "Empresas con lotes AAEF en estado ACK_FAILED")
    private List<CompanyStat> companiesWithFailedAck;

    /**
     * QA Bloque PA Bug 52 (HU-PA-15 E5, 2026-05-09): estado del ultimo run del job
     * nocturno de vencimiento de permisos temporales. Carlos (PLATFORM_ADMIN) lo
     * ve directo en el panel sin tener que entrar al endpoint dedicado.
     */
    @Schema(description = "Estado del ultimo run del scheduler de permisos temporales (HU-PA-15 E5)",
            example = "{\"status\":\"OK\",\"expiredCount\":3,\"durationMs\":42}")
    private java.util.Map<String, Object> tempPermSchedulerStatus;

    /**
     * QA Bloque PA Bug 68 (HU-PA-PLAT-06 E2, 2026-05-09): estado tiempo real de
     * servicios (database, aaef) con badge OK / WARNING / CRITICAL.
     */
    @Schema(description = "Salud de servicios (HU-PA-PLAT-06 E2)",
            example = "{\"database\":{\"status\":\"OK\",\"latencyMs\":4},\"aaef\":{\"status\":\"OK\",\"errorsLastHour\":0}}")
    private java.util.Map<String, Object> servicesHealth;

    /**
     * QA Bloque PA Bug 68 (HU-PA-PLAT-06 E3, 2026-05-09): metricas de uso.
     */
    @Schema(description = "Metricas de uso (HU-PA-PLAT-06 E3)",
            example = "{\"activeSessionsApprox\":12,\"errors5xxLastHour\":0}")
    private java.util.Map<String, Object> usageMetrics;

    /** QA Bloque PA Bug 87 (HU-PA-PLAT-06 E1): empresas creadas en los ultimos 30 dias. */
    @Schema(description = "Empresas creadas en los ultimos 30 dias", example = "3")
    private Long companiesCreatedLast30Days;

    /** QA Bloque PA Bug 87 (HU-PA-PLAT-06 E1): empresas activas sin actividad en 7 dias. */
    @Schema(description = "Empresas sin actividad reciente (sin eventos audit en 7 dias)", example = "2")
    private Long companiesWithoutActivityLast7Days;

    /** QA Bloque PA Bug 87 (HU-PA-PLAT-06 E1): distribucion por regimen tributario. */
    @Schema(description = "Distribucion de empresas por regimen tributario",
            example = "[{\"regimen\":\"Comun\",\"count\":8},{\"regimen\":\"Simplificado\",\"count\":4}]")
    private java.util.List<java.util.Map<String, Object>> companiesByRegimen;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Metricas por empresa")
    public static class CompanyStat {
        @Schema(description = "ID de la empresa", example = "2")
        private Long companyId;
        @Schema(description = "Razon social", example = "ACME SAS")
        private String companyName;
        @Schema(description = "Valor de la metrica", example = "85")
        private Long value;
    }
}
