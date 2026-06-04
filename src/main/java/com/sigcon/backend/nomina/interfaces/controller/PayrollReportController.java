package com.sigcon.backend.nomina.interfaces.controller;

import com.sigcon.backend.nomina.domain.service.PayrollReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * HU-NOM-06: reportes y exportaciones de nomina.
 *
 * <ul>
 *   <li>E1: Comprobante individual PDF (CST Art. 132)</li>
 *   <li>E2: Reporte PILA CSV (Decreto 1772/1994, Ley 100/1993)</li>
 *   <li>E3: Resumen contable del periodo con desglose por centro de costo
 *       y referencia a consecutivos CG</li>
 * </ul>
 */
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/nomina/reportes")
@RequiredArgsConstructor
@Tag(name = "Nomina - Reportes",
     description = "Comprobantes, PILA y resumen contable (HU-NOM-06)")
public class PayrollReportController {

    private final PayrollReportService service;

    @Operation(summary = "Comprobante individual de pago PDF (HU-NOM-06 E1)",
            description = "Genera el PDF del comprobante del empleado con detalle de devengados, "
                    + "deducciones, neto a pagar y firma del empleador. Solo disponible para "
                    + "recibos APROBADOS o CERRADOS (CST Art. 132).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF binario del comprobante"),
            @ApiResponse(responseCode = "400",
                    description = "Recibo en DRAFT o no existe")
    })
    @PreAuthorize("hasAuthority('PERM_NOM.COMPROBANTES.EXPORTAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping(value = "/comprobante/{receiptId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> receiptPdf(
            @Parameter(description = "ID del recibo", required = true, example = "1")
            @PathVariable Long receiptId) {
        byte[] pdf = service.generateReceiptPdf(receiptId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=comprobante-nomina-" + receiptId + ".pdf")
                .body(pdf);
    }

    @Operation(summary = "Reporte PILA del periodo en CSV / TXT plano / XLSX (HU-NOM-06 E2)",
            description = "Genera el archivo PILA compatible con operadores de seguridad social "
                    + "(Decreto 1772/1994, Ley 100/1993) en el formato indicado por el parametro "
                    + "'format' (csv | txt | xlsx; por defecto csv). Incluye por empleado: NIT "
                    + "empresa, DOC empleado, IBC, aportes empleado (4% salud + 4% pension) y "
                    + "empresa (8.5% + 12% + 2% SENA + 3% ICBF + 4% caja). Solo recibos APROBADOS "
                    + "o CERRADOS.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo PILA en el formato solicitado")
    })
    @PreAuthorize("hasAuthority('PERM_NOM.PILA.GENERAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping(value = "/pila")
    public ResponseEntity<byte[]> pila(
            @Parameter(description = "Año del periodo", required = true, example = "2026")
            @RequestParam Integer year,
            @Parameter(description = "Mes del periodo (1-12)", required = true, example = "4")
            @RequestParam Integer month,
            @Parameter(description = "Formato de descarga: csv | txt | xlsx", example = "xlsx")
            @RequestParam(required = false, defaultValue = "csv") String format) {
        // NOM-5 (2026-06-04): PILA en CSV (default), TXT plano y XLSX (Excel).
        String fmt = format == null ? "csv" : format.trim().toLowerCase();
        byte[] body;
        String mime;
        String ext;
        switch (fmt) {
            case "txt":
                body = service.generatePilaTxt(year, month);
                mime = "text/plain; charset=utf-8";
                ext = "txt";
                break;
            case "xlsx":
                body = service.generatePilaXlsx(year, month);
                mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                ext = "xlsx";
                break;
            default:
                body = service.generatePilaCsv(year, month);
                mime = "text/csv; charset=utf-8";
                ext = "csv";
                break;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=pila-" + year + "-"
                                + String.format("%02d", month) + "." + ext)
                .body(body);
    }

    @Operation(summary = "Resumen contable del periodo (HU-NOM-06 E3)",
            description = "Retorna JSON con: totales (devengados, deducciones, aportes, neto), "
                    + "desglose por centro de costo (earningsByCostCenter, netByCostCenter) y "
                    + "lista de journalEntryIds de los comprobantes contables generados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen contable del periodo")
    })
    @PreAuthorize("hasAuthority('PERM_NOM.LIQUIDACION.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping(value = "/resumen-contable", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> summary(
            @Parameter(description = "Año del periodo", required = true, example = "2026")
            @RequestParam Integer year,
            @Parameter(description = "Mes del periodo (1-12)", required = true, example = "4")
            @RequestParam Integer month) {
        return ResponseEntity.ok(service.periodAccountingSummary(year, month));
    }
}
