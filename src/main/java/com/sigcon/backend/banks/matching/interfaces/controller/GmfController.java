package com.sigcon.backend.banks.matching.interfaces.controller;

import com.sigcon.backend.banks.matching.domain.service.GmfService;
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
 * BNK-HU-061: Gravamen a los Movimientos Financieros (GMF / 4x1000).
 * Validación cruzada del período (E3) y reporte por cuenta+período (E4).
 */
@RestController
@RequestMapping("/api/v1/banks/gmf")
@RequiredArgsConstructor
@Tag(name = "BNK - GMF 4x1000 (HU-061)",
     description = "Validación y reporte del Gravamen a los Movimientos Financieros")
public class GmfController {

    private final GmfService gmfService;

    @Operation(summary = "Validación cruzada del GMF del período (BNK-HU-061 E3)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/validar/{bankAccountId}")
    public ResponseEntity<?> validar(@PathVariable Long bankAccountId) {
        return ResponseEntity.ok(gmfService.validate(bankAccountId));
    }

    @Operation(summary = "Reporte de GMF por cuenta y período (BNK-HU-061 E4)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/reporte")
    public ResponseEntity<?> reporte(@RequestParam Long bankAccountId,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(gmfService.report(bankAccountId, from, to));
    }

    @Operation(summary = "Exportar reporte GMF a Excel/CSV (BNK-HU-061 E4)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/reporte/export")
    public ResponseEntity<byte[]> export(@RequestParam Long bankAccountId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        byte[] data = gmfService.exportReport(bankAccountId, from, to, format);
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        String filename = "gmf_cuenta_" + bankAccountId + (xlsx ? ".xlsx" : ".csv");
        String mime = xlsx ? SimpleTableExporter.XLSX_MIME : SimpleTableExporter.CSV_MIME;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(mime))
                .body(data);
    }
}
