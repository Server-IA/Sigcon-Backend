package com.sigcon.backend.invoices.ap_reports.interfaces.controller;

import java.io.IOException;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.invoices.ap_reports.domain.service.ApReportService;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para reportes del modulo Cuentas por Pagar.
 * Provee endpoints para reporte de antiguedad de saldos (aging),
 * generacion de PDF y estado de cuenta por proveedor.
 */
@RestController
@RequestMapping("/api/v1/ap/reports")
@RequiredArgsConstructor
@Tag(name = "6. Cuentas por Pagar - Reportes", description = "Endpoints para reportes de cuentas por pagar")
public class ApReportController {

    private final ApReportService reportService;

    /**
     * Genera el reporte de antiguedad de saldos (aging) en formato JSON.
     * Clasifica facturas pendientes por rango de dias de vencimiento: 0-30, 31-60, 61-90, +90.
     *
     * @return reporte de antiguedad con totales y detalle por factura
     */
    @Operation(summary = "Reporte de antiguedad (JSON)", description = "Genera el reporte de antiguedad de saldos clasificado por rangos de dias de vencimiento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte de antiguedad generado")
    })
    @PostMapping("/aging")
    @PreAuthorize("hasAuthority('PERM_READ_AP_REPORT') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAgingReport() {
        return reportService.getAgingReport();
    }

    /**
     * Genera el reporte de antiguedad de saldos en formato PDF para descarga.
     *
     * @return archivo PDF con el reporte de antiguedad
     */
    @Operation(summary = "Reporte de antiguedad (PDF)", description = "Genera el reporte de antiguedad de saldos en formato PDF para descarga")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generado exitosamente"),
            @ApiResponse(responseCode = "500", description = "Error al generar el PDF")
    })
    @PostMapping("/aging/pdf")
    @PreAuthorize("hasAuthority('PERM_READ_AP_REPORT') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAgingReportPdf() {
        try {
            return reportService.generateAgingPdf();
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al generar el PDF: " + e.getMessage())));
        }
    }

    /**
     * Genera el estado de cuenta de un proveedor especifico.
     * Incluye facturas, pagos y notas credito/debito con saldos.
     *
     * @param thirdPartyId identificador del tercero (proveedor)
     * @return estado de cuenta del proveedor
     */
    @Operation(summary = "Estado de cuenta proveedor", description = "Genera el estado de cuenta detallado de un proveedor con facturas, pagos y notas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado de cuenta generado"),
            @ApiResponse(responseCode = "400", description = "Proveedor no encontrado")
    })
    @PostMapping("/supplier/{thirdPartyId}")
    @PreAuthorize("hasAuthority('PERM_READ_AP_REPORT') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getSupplierStatement(@PathVariable Long thirdPartyId) {
        try {
            return reportService.getSupplierStatement(thirdPartyId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}
