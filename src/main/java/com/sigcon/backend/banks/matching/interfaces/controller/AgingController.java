package com.sigcon.backend.banks.matching.interfaces.controller;

import com.sigcon.backend.banks.matching.domain.service.AgingService;
import com.sigcon.backend.utils.export.SimpleTableExporter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * BNK-HU-074: antigüedad de partidas conciliatorias — reporte, dashboard,
 * alertas, cheques por caducar y resolución manual.
 */
@RestController
@RequestMapping("/api/v1/banks/partidas-antiguedad")
@RequiredArgsConstructor
@Tag(name = "BNK - Antigüedad de partidas (HU-074)",
     description = "Buckets de antigüedad, alertas 60/90 días, dashboard y cheques por caducar")
public class AgingController {

    private final AgingService service;
    private static final String VER = "hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')";
    private static final String EDITAR = "hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')";

    @Operation(summary = "Reporte de partidas pendientes con buckets (BNK-HU-074 E5)")
    @PreAuthorize(VER)
    @GetMapping("/reporte")
    public ResponseEntity<?> reporte(@RequestParam(required = false) Long bankAccountId,
                                     @RequestParam(required = false) Integer diasMin,
                                     @RequestParam(required = false) Integer diasMax,
                                     @RequestParam(required = false) String tipo) {
        return ResponseEntity.ok(service.report(bankAccountId, diasMin, diasMax, tipo));
    }

    @Operation(summary = "Dashboard de partidas pendientes (BNK-HU-074 E6)")
    @PreAuthorize(VER)
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(@RequestParam(required = false) Long bankAccountId) {
        return ResponseEntity.ok(service.dashboard(bankAccountId));
    }

    @Operation(summary = "Cheques próximos a caducar (BNK-HU-074 E7)")
    @PreAuthorize(VER)
    @GetMapping("/cheques-caducar")
    public ResponseEntity<?> chequesCaducar() {
        return ResponseEntity.ok(service.chequesProximosCaducar());
    }

    @Operation(summary = "Recalcular antigüedad + disparar alertas del tenant (BNK-HU-074 E1/E3/E4)")
    @PreAuthorize(EDITAR)
    @PostMapping("/recalcular")
    public ResponseEntity<?> recalcular() {
        int n = service.recalcForCurrentTenant();
        var alertas = service.runAlertsForCurrentTenant();
        return ResponseEntity.ok(java.util.Map.of("recalculadas", n, "alertas", alertas));
    }

    @Operation(summary = "Resolver partida manualmente: AJUSTE o PROXIMO_PERIODO (BNK-HU-074 E8)")
    @PreAuthorize(EDITAR)
    @PostMapping("/{partidaId}/resolver")
    public ResponseEntity<?> resolver(@PathVariable Long partidaId, @RequestBody ResolverPartidaRequest req) {
        return ResponseEntity.ok(service.resolver(partidaId, req.getTipoResolucion(), req.getComprobanteId(), req.getMotivo()));
    }

    @Operation(summary = "Exportar reporte de partidas pendientes a Excel/CSV (BNK-HU-074 E5)")
    @PreAuthorize(VER)
    @GetMapping("/reporte/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long bankAccountId,
                                         @RequestParam(required = false) Integer diasMin,
                                         @RequestParam(required = false) Integer diasMax,
                                         @RequestParam(required = false) String tipo,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        byte[] data = service.exportReport(bankAccountId, diasMin, diasMax, tipo, format);
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        String filename = "partidas_pendientes." + (xlsx ? "xlsx" : "csv");
        String mime = xlsx ? SimpleTableExporter.XLSX_MIME : SimpleTableExporter.CSV_MIME;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(mime))
                .body(data);
    }

    @Data
    public static class ResolverPartidaRequest {
        private String tipoResolucion; // AJUSTE | PROXIMO_PERIODO
        private Long comprobanteId;
        private String motivo;
    }
}
