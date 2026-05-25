package com.sigcon.backend.assets.reports.interfaces.controller;

import com.sigcon.backend.assets.reports.application.AssetReportRequest;
import com.sigcon.backend.assets.reports.domain.service.AssetReportService;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Controlador REST para la generacion de reportes del modulo de activos fijos.
 *
 * <p>ACT-04: Reporte de activos con filtros por fecha y agrupamiento.<br>
 * ACT-07: Exportacion de reporte en formato PDF.</p>
 *
 * @see com.sigcon.backend.assets.reports.domain.service.AssetReportService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assets/reports")
@RequiredArgsConstructor
@Tag(name = "8. Modulo de Activos - Reportes",
     description = "Endpoints para generacion de reportes de activos fijos")
public class AssetReportController {

    private final AssetReportService assetReportService;

    /**
     * Genera un reporte de activos en formato JSON con datos agrupados.
     *
     * @param request    parametros del reporte (fechas y criterio de agrupamiento)
     * @param bindingResult resultado de validacion
     * @return datos del reporte agrupados segun criterio seleccionado
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_ASSET_REPORT','TEMP_PERM_VIEW_ASSET_REPORT','TEMP_VIEW_ASSET_REPORT','PERM_ACT.ACTIVOS.EXPORTAR_REPORTE','TEMP_PERM_ACT.ACTIVOS.EXPORTAR_REPORTE','TEMP_ACT.ACTIVOS.EXPORTAR_REPORTE','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "Generar reporte de activos (JSON)",
               description = "ACT-04: Genera un reporte de activos filtrado por rango de fechas "
                       + "de adquisicion y agrupado por clasificacion, periodo o sin agrupar.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reporte generado correctamente"),
            @ApiResponse(responseCode = "400", description = "Error de validacion en los parametros",
                         content = @Content(schema = @Schema(implementation = ErrorRespondJson.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes"),
            @ApiResponse(responseCode = "500", description = "Error interno al generar el reporte")
    })
    public ResponseEntity<?> generateAssetReport(
            @Valid @RequestBody AssetReportRequest request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        try {
            // QA Activos (2026-05-25) Error 03: pasar el request completo para
            // que se apliquen TODOS los filtros (proveedor, clasificacion PUC,
            // estado, tipo), no solo el rango de fechas.
            var reportData = assetReportService.generateAssetReport(request);

            // HU-ACT-04 E3 (QA 2026-05-05): si el reporte sale vacio devolver
            // mensaje literal del Excel para que el frontend no genere archivo.
            int totalRows = reportData == null ? 0 : reportData.values().stream()
                    .mapToInt(list -> list == null ? 0 : list.size()).sum();
            String okMsg = totalRows == 0
                    ? "No hay activos registrados con los criterios seleccionados."
                    : "Reporte de activos generado exitosamente.";

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of(okMsg),
                            Optional.of(reportData)));

        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
            throw __tie;
        } catch (Exception e) {
            log.error("Error al generar reporte de activos: {}", e.getMessage(), e);
            // HU-ACT-04 E6: mensaje literal del Excel para errores internos.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("Sin Conexion, no se pudo conectar con el servidor despues de reintentar, verifique su conexion e intentelo nuevamente.")));
        }
    }

    /**
     * Genera un reporte de activos en formato PDF para descarga.
     *
     * @param request    parametros del reporte (fechas y criterio de agrupamiento)
     * @param bindingResult resultado de validacion
     * @return archivo PDF como bytes con Content-Type application/pdf
     */
    @PostMapping("/generate/pdf")
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_ASSET_REPORT','TEMP_PERM_VIEW_ASSET_REPORT','TEMP_VIEW_ASSET_REPORT','PERM_ACT.ACTIVOS.EXPORTAR_REPORTE','TEMP_PERM_ACT.ACTIVOS.EXPORTAR_REPORTE','TEMP_ACT.ACTIVOS.EXPORTAR_REPORTE','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "Generar reporte de activos (PDF)",
               description = "ACT-07: Genera y descarga un reporte de activos en formato PDF "
                       + "filtrado por rango de fechas y con agrupamiento opcional.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "PDF generado correctamente",
                         content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "400", description = "Error de validacion en los parametros",
                         content = @Content(schema = @Schema(implementation = ErrorRespondJson.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes"),
            @ApiResponse(responseCode = "500", description = "Error interno al generar el PDF")
    })
    public ResponseEntity<?> generateAssetReportPdf(
            @Valid @RequestBody AssetReportRequest request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        try {
            // QA Activos (2026-05-25) Error 03: PDF con todos los filtros aplicados.
            byte[] pdfBytes = assetReportService.generateAssetReportPdf(request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "reporte_activos.pdf");
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
            throw __tie;
        } catch (Exception e) {
            log.error("Error al generar PDF de reporte de activos: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("Error al generar el PDF: " + e.getMessage())));
        }
    }
}
