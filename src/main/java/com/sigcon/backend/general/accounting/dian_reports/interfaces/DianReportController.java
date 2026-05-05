package com.sigcon.backend.general.accounting.dian_reports.interfaces;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.general.accounting.dian_reports.application.DianReportRequest;
import com.sigcon.backend.general.accounting.dian_reports.application.DianReportResponse;
import com.sigcon.backend.general.accounting.dian_reports.domain.service.DianReportService;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador REST para los reportes de Informacion Exogena DIAN
 * (Formatos F1001, F1007, F1008).
 *
 * <p>La Informacion Exogena corresponde al reporte anual que los
 * contribuyentes deben presentar ante la Direccion de Impuestos y
 * Aduanas Nacionales (DIAN) con el detalle por tercero de los pagos,
 * ingresos, retenciones y saldos del anio gravable.</p>
 */
@RestController
@RequestMapping("/api/v1/cg/dian-reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "5. Contabilidad General - Reportes DIAN",
     description = "Generacion de reportes de Informacion Exogena DIAN (F1001, F1007, F1008)")
@SecurityRequirement(name = "bearerAuth")
public class DianReportController {

    private final DianReportService dianReportService;

    @PostMapping("/generate")
    @Operation(
        summary = "Generar reporte DIAN",
        description = "Genera un reporte de Informacion Exogena DIAN segun el formato (F1001, "
                + "F1007 o F1008) y el anio gravable indicados. Retorna el detalle por tercero."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reporte generado correctamente"),
        @ApiResponse(responseCode = "400", description = "Formato o anio invalido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_READ_DIAN_REPORT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> generate(@Valid @RequestBody DianReportRequest request) {
        try {
            DianReportResponse result = dispatch(request.getFormat(), request.getYear());
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                    Optional.of("Reporte DIAN " + request.getFormat() + " generado correctamente"),
                    Optional.of(result)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Error generando DIAN {}: {}", request.getFormat(), e.getMessage());
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @GetMapping("/{format}/{year}/csv")
    @Operation(
        summary = "Descargar reporte DIAN en CSV",
        description = "Descarga el reporte DIAN indicado como archivo CSV (UTF-8 con BOM)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Archivo CSV generado"),
        @ApiResponse(responseCode = "400", description = "Formato o anio invalido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_READ_DIAN_REPORT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> downloadCsv(@PathVariable String format, @PathVariable Integer year) {
        try {
            DianReportResponse result = dispatch(format, year);
            byte[] csv = dianReportService.exportToCsv(result);
            String filename = "DIAN_" + format + "_" + year + ".csv";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(csv.length);

            return new ResponseEntity<>(csv, headers, 200);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Despacha la generacion segun el formato solicitado.
     */
    private DianReportResponse dispatch(String format, Integer year) {
        if (format == null) {
            throw new IllegalArgumentException("Formato es obligatorio");
        }
        switch (format.toUpperCase()) {
            case "F1001": return dianReportService.generateF1001(year);
            case "F1007": return dianReportService.generateF1007(year);
            case "F1008": return dianReportService.generateF1008(year);
            default:
                throw new IllegalArgumentException("Formato DIAN no soportado: " + format);
        }
    }
}
