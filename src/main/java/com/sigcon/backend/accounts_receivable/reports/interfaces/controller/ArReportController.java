package com.sigcon.backend.accounts_receivable.reports.interfaces.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.accounts_receivable.reports.application.ArReportRequest;
import com.sigcon.backend.accounts_receivable.reports.domain.service.ArReportService;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * AR-05, AR-10, AR-12: Controlador REST de reportes de Cuentas por Cobrar.
 */
@RestController
@RequestMapping("/api/v1/ar")
@RequiredArgsConstructor
@Tag(name = "7. Cuentas por Cobrar - Reportes",
     description = "Endpoints de reportes: por cliente, estado, periodo, aging, vencidas, saldo y estado de cuenta")
public class ArReportController {

    private final ArReportService service;

    /** AR-05: Reporte por cliente. */
    @Operation(summary = "Reporte CxC por cliente",
               description = "Agrupa facturas por cliente con totales facturado y pendiente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reporte generado"),
        @ApiResponse(responseCode = "400", description = "Parametros invalidos")
    })
    @PostMapping("/reports/by-customer")
    @PreAuthorize("hasAuthority('PERM_READ_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> byCustomer(@RequestBody ArReportRequest request) {
        try {
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                    Optional.of("Reporte generado"),
                    Optional.of(service.reportByCustomer(request))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /** HU-AR-05 E2: solo facturas con saldo pendiente real (excluye PAID/VOIDED/SETTLED/DRAFT). */
    @Operation(summary = "Reporte CxC solo pendientes",
               description = "HU-AR-05 E2: lista facturas con balanceDue > 0 sin necesidad de elegir status especifico. Filtra opcionalmente por cliente y/o rango de fechas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reporte generado"),
        @ApiResponse(responseCode = "400", description = "Parametros invalidos")
    })
    @PostMapping("/reports/only-pending")
    @PreAuthorize("hasAuthority('PERM_READ_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> onlyPending(@RequestBody(required = false) ArReportRequest request) {
        try {
            if (request == null) request = new ArReportRequest();
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                    Optional.of("Reporte de facturas pendientes generado"),
                    Optional.of(service.reportOnlyPending(request))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /** AR-05: Reporte por estado. */
    @Operation(summary = "Reporte CxC por estado",
               description = "Lista facturas filtradas por estado y rango de fechas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reporte generado"),
        @ApiResponse(responseCode = "400", description = "Parametros invalidos")
    })
    @PostMapping("/reports/by-status")
    @PreAuthorize("hasAuthority('PERM_READ_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> byStatus(@RequestBody ArReportRequest request) {
        try {
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                    Optional.of("Reporte generado"),
                    Optional.of(service.reportByStatus(request))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /** AR-05: Resumen por periodo. */
    @Operation(summary = "Resumen CxC por periodo",
               description = "Totales facturado, cobrado y pendiente en el rango de fechas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reporte generado"),
        @ApiResponse(responseCode = "400", description = "Parametros invalidos")
    })
    @PostMapping("/reports/by-period")
    @PreAuthorize("hasAuthority('PERM_READ_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> byPeriod(@RequestBody ArReportRequest request) {
        try {
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                    Optional.of("Reporte generado"),
                    Optional.of(service.reportByPeriod(request))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /** AR-05: Genera PDF del reporte indicado. */
    @Operation(summary = "Generar PDF de reporte CxC",
               description = "Tipo valido: by-customer | by-status | by-period | aging | overdue | upcoming")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PDF generado"),
        @ApiResponse(responseCode = "400", description = "Tipo o parametros invalidos")
    })
    @PostMapping("/reports/{type}/pdf")
    @PreAuthorize("hasAuthority('PERM_READ_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> generatePdf(@PathVariable String type,
                                          @RequestBody(required = false) ArReportRequest request) {
        try {
            if (request == null) request = new ArReportRequest();
            byte[] pdf = service.generatePdf(type, request);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"reporte_cxc_" + type + ".pdf\"")
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("Error generando PDF: " + e.getMessage())));
        }
    }

    /** AR-10: Aging de cartera. */
    @Operation(summary = "Aging de cartera CxC",
               description = "Agrupa facturas vencidas en buckets 0-30, 31-60, 61-90 y +90 dias")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Aging generado")
    })
    @GetMapping("/reports/aging")
    @PreAuthorize("hasAuthority('PERM_READ_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> aging() {
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Aging generado"), Optional.of(service.aging())));
    }
}
