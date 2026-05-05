package com.sigcon.backend.general.accounting.tax_reports.interfaces;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.general.accounting.tax_reports.application.EclProvisionReportDTO;
import com.sigcon.backend.general.accounting.tax_reports.application.ExchangeDifferenceReportDTO;
import com.sigcon.backend.general.accounting.tax_reports.application.IvaReportDTO;
import com.sigcon.backend.general.accounting.tax_reports.application.TaxesSummaryDTO;
import com.sigcon.backend.general.accounting.tax_reports.domain.service.TaxReportService;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador REST de reportes contables-tributarios (HU-CG-31 a HU-CG-34).
 * <ul>
 *   <li>HU-CG-31: Provision ECL de cartera (NIIF 9)</li>
 *   <li>HU-CG-32: Cuadre IVA bimestral (insumo Formulario 300 DIAN)</li>
 *   <li>HU-CG-33: Diferencias en cambio (NIC 21)</li>
 *   <li>HU-CG-34: Resumen consolidado anual de impuestos y retenciones</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/cg/tax-reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "5. Contabilidad General - Reportes Tributarios",
     description = "Reportes de Provision ECL, IVA bimestral, diferencias en cambio y resumen de impuestos")
@SecurityRequirement(name = "bearerAuth")
public class TaxReportController {

    private final TaxReportService taxReportService;

    /**
     * HU-CG-31: calcula la provision ECL de cartera al cierre del anio.
     */
    @GetMapping("/ecl")
    @Operation(summary = "Provision ECL de cartera (HU-CG-31)",
        description = "Calcula la Perdida Crediticia Esperada sobre cuentas por cobrar al 31-dic "
                    + "del anio, aplicando tasas NIIF 9 por tramos de mora.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reporte generado"),
        @ApiResponse(responseCode = "400", description = "Parametros invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_TAX_REPORT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getEclProvision(@RequestParam Integer year) {
        try {
            EclProvisionReportDTO result = taxReportService.generateEclProvision(year);
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Provision ECL generada correctamente"),
                Optional.of(result)));
        } catch (IllegalArgumentException e) {
            log.warn("Error generando ECL: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * HU-CG-32: cuadre de IVA bimestral (insumo Formulario 300).
     */
    @GetMapping("/iva")
    @Operation(summary = "Cuadre IVA bimestral (HU-CG-32)",
        description = "Calcula el IVA generado (ventas) vs IVA descontable (compras) de un "
                    + "bimestre especifico del anio. Sirve como insumo para el Formulario 300 DIAN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reporte generado"),
        @ApiResponse(responseCode = "400", description = "Parametros invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_TAX_REPORT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getIvaBimestral(@RequestParam Integer year,
                                             @RequestParam Integer bimester) {
        try {
            IvaReportDTO result = taxReportService.generateIvaBimestral(year, bimester);
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Cuadre IVA bimestral generado correctamente"),
                Optional.of(result)));
        } catch (IllegalArgumentException e) {
            log.warn("Error generando IVA: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * HU-CG-33: diferencias en cambio al cierre del mes.
     */
    @GetMapping("/exchange-differences")
    @Operation(summary = "Diferencias en cambio (HU-CG-33)",
        description = "Calcula las diferencias en cambio por revaluacion de partidas monetarias "
                    + "en moneda extranjera al cierre del mes indicado (NIC 21).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reporte generado"),
        @ApiResponse(responseCode = "400", description = "Parametros invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_TAX_REPORT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getExchangeDifferences(@RequestParam Integer year,
                                                    @RequestParam Integer month) {
        try {
            ExchangeDifferenceReportDTO result = taxReportService.generateExchangeDifferences(year, month);
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Diferencias en cambio generadas correctamente"),
                Optional.of(result)));
        } catch (IllegalArgumentException e) {
            log.warn("Error generando diferencias en cambio: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * HU-CG-34: resumen consolidado anual de impuestos y retenciones.
     */
    @GetMapping("/taxes-summary")
    @Operation(summary = "Resumen consolidado de impuestos y retenciones (HU-CG-34)",
        description = "Devuelve el total anual y el desglose mensual de IVA generado, IVA "
                    + "descontable, saldo IVA, retenciones practicadas y retenciones soportadas.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reporte generado"),
        @ApiResponse(responseCode = "400", description = "Parametros invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_TAX_REPORT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getTaxesSummary(@RequestParam Integer year) {
        try {
            TaxesSummaryDTO result = taxReportService.generateTaxesSummary(year);
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Resumen de impuestos generado correctamente"),
                Optional.of(result)));
        } catch (IllegalArgumentException e) {
            log.warn("Error generando resumen impuestos: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}
