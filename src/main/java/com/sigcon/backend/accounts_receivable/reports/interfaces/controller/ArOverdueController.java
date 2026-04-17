package com.sigcon.backend.accounts_receivable.reports.interfaces.controller;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.accounts_receivable.reports.domain.service.ArReportService;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * AR-10, AR-12: Endpoints de consulta rapida de facturas vencidas,
 * proximas a vencer y saldo por cliente.
 */
@RestController
@RequestMapping("/api/v1/ar")
@RequiredArgsConstructor
@Tag(name = "7. Cuentas por Cobrar - Reportes",
     description = "Consulta de vencidas, proximas a vencer y saldos por cliente")
public class ArOverdueController {

    private final ArReportService service;

    /** AR-10: Facturas vencidas. */
    @Operation(summary = "Listar facturas vencidas",
               description = "Retorna facturas con saldo pendiente y fecha vencida, ordenadas por dias de mora desc")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado obtenido")
    })
    @GetMapping("/invoices/overdue")
    @PreAuthorize("hasAuthority('PERM_READ_SALES_INVOICE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> overdue(@RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Facturas vencidas"), Optional.of(service.listOverdue(days))));
    }

    /** AR-10: Facturas proximas a vencer. */
    @Operation(summary = "Listar facturas proximas a vencer",
               description = "Retorna facturas con saldo pendiente y fecha de vencimiento en los proximos N dias")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado obtenido")
    })
    @GetMapping("/invoices/upcoming")
    @PreAuthorize("hasAuthority('PERM_READ_SALES_INVOICE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> upcoming(@RequestParam(required = false, defaultValue = "7") Integer days) {
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Facturas proximas a vencer"), Optional.of(service.listUpcoming(days))));
    }

    /** AR-12: Saldo pendiente de un cliente. */
    @Operation(summary = "Saldo pendiente por cliente",
               description = "Retorna el saldo total pendiente y el listado de facturas abiertas del cliente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Saldo obtenido"),
        @ApiResponse(responseCode = "400", description = "Cliente no encontrado")
    })
    @GetMapping("/customers/{thirdPartyId}/balance")
    @PreAuthorize("hasAuthority('PERM_READ_SALES_INVOICE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> customerBalance(@PathVariable Long thirdPartyId,
                                              @RequestParam(required = false) Integer year,
                                              @RequestParam(required = false) Integer month) {
        try {
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                    Optional.of("Saldo del cliente"),
                    Optional.of(service.customerBalance(thirdPartyId, year, month))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /** AR-12: Estado de cuenta del cliente. */
    @Operation(summary = "Estado de cuenta del cliente",
               description = "Estado de cuenta con facturas, cobros, notas y anticipos en el rango de fechas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado de cuenta obtenido"),
        @ApiResponse(responseCode = "400", description = "Cliente o parametros invalidos")
    })
    @GetMapping("/customers/{thirdPartyId}/statement")
    @PreAuthorize("hasAuthority('PERM_READ_SALES_INVOICE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> customerStatement(@PathVariable Long thirdPartyId,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                    Optional.of("Estado de cuenta"),
                    Optional.of(service.customerStatement(thirdPartyId, startDate, endDate))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}
