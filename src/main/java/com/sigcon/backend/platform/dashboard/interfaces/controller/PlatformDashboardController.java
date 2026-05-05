package com.sigcon.backend.platform.dashboard.interfaces.controller;

import com.sigcon.backend.platform.dashboard.application.PlatformDashboardDTO;
import com.sigcon.backend.platform.dashboard.domain.service.PlatformDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint del dashboard de plataforma (HU-PA-PLAT-06).
 *
 * <p>Solo accesible para {@code PLATFORM_ADMIN}. Devuelve KPIs agregados
 * cross-empresa en una sola llamada. El frontend consume este endpoint y
 * renderiza cards + graficas en {@code /platform/dashboard}.
 */
@RestController
@RequestMapping("/api/platform/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
@Tag(name = "Plataforma - Dashboard",
     description = "Indicadores agregados cross-empresa (HU-PA-PLAT-06). Solo PLATFORM_ADMIN.")
public class PlatformDashboardController {

    private final PlatformDashboardService service;

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping
    @Operation(summary = "Obtener KPIs globales de la plataforma",
               description = "Devuelve conteos agregados de empresas, usuarios, JE ultimos 6 meses, "
                           + "lotes AAEF totales y fallidos, plus top-5 empresas por volumen de JE "
                           + "y empresas con ACKs fallidos que requieren atencion.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "KPIs calculados correctamente"),
        @ApiResponse(responseCode = "401", description = "Token ausente o invalido"),
        @ApiResponse(responseCode = "403", description = "Usuario sin rol PLATFORM_ADMIN")
    })
    public ResponseEntity<PlatformDashboardDTO> getDashboard() {
        return ResponseEntity.ok(service.getDashboard());
    }
}
