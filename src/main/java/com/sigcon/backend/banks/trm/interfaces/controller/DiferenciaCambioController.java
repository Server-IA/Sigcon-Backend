package com.sigcon.backend.banks.trm.interfaces.controller;

import com.sigcon.backend.banks.trm.domain.service.DiferenciaCambioService;
import com.sigcon.backend.utils.export.SimpleTableExporter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * BNK-HU-076 E4-E7: cálculo de diferencia en cambio al cierre, asiento propuesto
 * y reporte de moneda extranjera exportable.
 */
@RestController
@RequestMapping("/api/v1/banks/diferencia-cambio")
@RequiredArgsConstructor
@Tag(name = "BNK - Diferencia en cambio (HU-076)",
     description = "Cálculo NIC 21 al cierre, asiento de diferencia en cambio y reporte de moneda extranjera")
public class DiferenciaCambioController {

    private final DiferenciaCambioService service;
    private static final String VER = "hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')";
    private static final String EDITAR = "hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')";

    @Operation(summary = "Calcular diferencia en cambio al cierre (BNK-HU-076 E4/E5)")
    @PreAuthorize(VER)
    @GetMapping("/calcular")
    public ResponseEntity<?> calcular(@RequestParam Long bankAccountId,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCierre) {
        return ResponseEntity.ok(service.calcular(bankAccountId, fechaCierre));
    }

    @Operation(summary = "Generar asiento de diferencia en cambio en BORRADOR (BNK-HU-076 E6)")
    @PreAuthorize(EDITAR)
    @PostMapping("/generar-asiento")
    public ResponseEntity<?> generarAsiento(@RequestParam Long bankAccountId,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCierre) {
        return ResponseEntity.ok(service.generarAsiento(bankAccountId, fechaCierre));
    }

    @Operation(summary = "Reporte de movimientos en moneda extranjera (BNK-HU-076 E7)")
    @PreAuthorize(VER)
    @GetMapping("/reporte")
    public ResponseEntity<?> reporte(@RequestParam Long bankAccountId,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCierre,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(service.reporte(bankAccountId, fechaCierre, desde, hasta));
    }

    @Operation(summary = "Exportar reporte de moneda extranjera a Excel/CSV (BNK-HU-076 E7)")
    @PreAuthorize(VER)
    @GetMapping("/reporte/export")
    public ResponseEntity<byte[]> export(@RequestParam Long bankAccountId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCierre,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        byte[] data = service.exportReporte(bankAccountId, fechaCierre, desde, hasta, format);
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        String filename = "moneda_extranjera." + (xlsx ? "xlsx" : "csv");
        String mime = xlsx ? SimpleTableExporter.XLSX_MIME : SimpleTableExporter.CSV_MIME;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(mime))
                .body(data);
    }
}
