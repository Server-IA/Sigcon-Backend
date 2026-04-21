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
