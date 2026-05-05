package com.sigcon.backend.invoices.ap_alerts.interfaces;

import com.sigcon.backend.invoices.ap_alerts.application.ApAlertDTO;
import com.sigcon.backend.invoices.ap_alerts.domain.service.ApAlertsService;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * AP-11: Controlador de alertas de facturas de compra.
 *
 * <p>Expone dos endpoints de solo lectura para consultar facturas en estado abierto
 * (PENDING / PARTIALLY_PAID) que estan proximas a vencer o ya vencieron.
 */
@RestController
@RequestMapping("/api/v1/ap/alerts")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "6. Cuentas por Pagar - Alertas",
    description = "Alertas de facturas proximas a vencer y vencidas (AP-11)"
)
public class ApAlertsController {

    private final ApAlertsService apAlertsService;

    /**
     * Facturas proximas a vencer dentro de N dias (default 7).
     *
     * @param daysAhead horizonte en dias (1-90). Opcional.
     */
    @GetMapping("/upcoming")
    @PreAuthorize("hasAuthority('PERM_VIEW_AP_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(
        summary = "Listar facturas proximas a vencer",
        description = "Retorna facturas con estado abierto cuyo vencimiento ocurre en los proximos N dias. "
                    + "Severidad: WARNING (0-3 dias), INFO (4-N dias)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado generado exitosamente"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "500", description = "Error interno")
    })
    public ResponseEntity<?> getUpcoming(
            @RequestParam(value = "daysAhead", required = false) Integer daysAhead) {
        try {
            List<ApAlertDTO> result = apAlertsService.getUpcomingInvoices(daysAhead);
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                    Optional.of("Facturas proximas a vencer"), Optional.of(result)));
        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
            throw __tie;
        } catch (Exception e) {
            log.error("Error obteniendo facturas proximas a vencer", e);
            return ResponseEntity.internalServerError().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Facturas vencidas (dias hasta vencimiento negativos).
     */
    @GetMapping("/overdue")
    @PreAuthorize("hasAuthority('PERM_VIEW_AP_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(
        summary = "Listar facturas vencidas",
        description = "Retorna facturas con estado abierto cuya fecha de vencimiento ya paso. "
                    + "Severidad siempre CRITICAL. El daysUntilDue devuelto es negativo."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado generado exitosamente"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "500", description = "Error interno")
    })
    public ResponseEntity<?> getOverdue() {
        try {
            List<ApAlertDTO> result = apAlertsService.getOverdueInvoices();
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                    Optional.of("Facturas vencidas"), Optional.of(result)));
        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
            throw __tie;
        } catch (Exception e) {
            log.error("Error obteniendo facturas vencidas", e);
            return ResponseEntity.internalServerError().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * HU-AP-10 E2 (Bloque AS): endpoint admin para disparar manualmente el
     * scheduler de notificaciones a roles suscritos. Util para QA y para
     * notificar inmediatamente cuando aparece una factura urgente sin esperar
     * al cron diario 1:30 AM.
     */
    @PostMapping("/run-overdue-scheduler")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "HU-AP-10 E2: dispara manualmente el job de notificaciones diario",
        description = "Ejecuta el scheduler que publica notificaciones in-app a los usuarios "
                    + "cuyos roles esten suscritos al evento AP_INVOICE_OVERDUE. Solo admin.")
    public ResponseEntity<?> runOverdueScheduler() {
        try {
            apAlertsService.logDailyOverdueSummary();
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                    Optional.of("Scheduler ejecutado. Notificaciones publicadas a roles suscritos."),
                    Optional.empty()));
        } catch (Exception e) {
            log.error("Error ejecutando scheduler manual", e);
            return ResponseEntity.internalServerError().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}
