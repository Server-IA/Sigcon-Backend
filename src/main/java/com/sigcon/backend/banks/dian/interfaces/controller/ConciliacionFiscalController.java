package com.sigcon.backend.banks.dian.interfaces.controller;

import com.sigcon.backend.banks.dian.domain.service.ConciliacionFiscalService;
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
 * BNK-HU-080: conciliación fiscal (art. 772-1 ET) para formatos 2516/2517.
 * El archivo en estructura XML/XSD oficial DIAN es infraestructura diferida; se exponen
 * datos (NIIF vs fiscal, diferencias, GMF, diferencia en cambio), notas, export CSV + retención.
 */
@RestController
@RequestMapping("/api/v1/banks/conciliacion-fiscal")
@RequiredArgsConstructor
@Tag(name = "BNK - Conciliación fiscal DIAN (HU-080)",
     description = "Saldos NIIF vs fiscal, diferencias temporarias/permanentes, GMF, formatos 2516/2517")
public class ConciliacionFiscalController {

    private final ConciliacionFiscalService service;
    private static final String VER = "hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')";
    private static final String EDITAR = "hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')";

    @Operation(summary = "Generar conciliación fiscal del año (BNK-HU-080 E1-E4)")
    @PreAuthorize(VER)
    @GetMapping("")
    public ResponseEntity<?> generar(@RequestParam int ano) {
        return ResponseEntity.ok(service.generar(ano));
    }

    @Operation(summary = "Agregar/actualizar nota explicativa por partida (BNK-HU-080 E6)")
    @PreAuthorize(EDITAR)
    @PostMapping("/nota")
    public ResponseEntity<?> nota(@RequestBody NotaRequest req) {
        var n = service.upsertNota(req.getAno(), req.getPartidaKey(), req.getNota());
        return ResponseEntity.ok(java.util.Map.of("id", n.getId(), "partidaKey", n.getPartidaKey()));
    }

    @Operation(summary = "Listar notas del año (BNK-HU-080 E6)")
    @PreAuthorize(VER)
    @GetMapping("/notas")
    public ResponseEntity<?> notas(@RequestParam int ano) {
        return ResponseEntity.ok(service.listarNotas(ano));
    }

    @Operation(summary = "Exportar formato 2516/2517 a CSV + conservar 10 años (BNK-HU-080 E5/E7)")
    @PreAuthorize(VER)
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam int ano, @RequestParam(defaultValue = "2516") String formato) {
        byte[] data = service.exportar(ano, formato);
        String fmt = "2517".equals(formato) ? "2517" : "2516";
        String filename = "conciliacion_fiscal_" + fmt + "_" + ano + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(SimpleTableExporter.CSV_MIME))
                .body(data);
    }

    @Data
    public static class NotaRequest {
        private int ano;
        private String partidaKey;
        private String nota;
    }
}
