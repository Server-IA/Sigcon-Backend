package com.sigcon.backend.banks.dian.interfaces.controller;

import com.sigcon.backend.banks.dian.domain.service.ExogenaService;
import com.sigcon.backend.utils.export.SimpleTableExporter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * BNK-HU-079: información exógena DIAN (formatos 1647/1010/1011) por año fiscal.
 * El archivo en estructura XML/XSD oficial DIAN es infraestructura diferida; se exponen
 * datos + validación + export CSV/XML genérico + histórico + retención.
 */
@RestController
@RequestMapping("/api/v1/banks/exogena")
@RequiredArgsConstructor
@Tag(name = "BNK - Información exógena DIAN (HU-079)",
     description = "Formatos 1647 (movimientos bancarios), 1010 (terceros), 1011 (información tributaria)")
public class ExogenaController {

    private final ExogenaService service;
    private static final String VER = "hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')";

    @Operation(summary = "Datos consolidados del formato exógena por año (BNK-HU-079 E1/E2)")
    @PreAuthorize(VER)
    @GetMapping("/{formato}")
    public ResponseEntity<?> datos(@PathVariable String formato, @RequestParam int ano) {
        return ResponseEntity.ok(service.datos(ano, formato));
    }

    @Operation(summary = "Validación previa antes de exportar (BNK-HU-079 E4)")
    @PreAuthorize(VER)
    @GetMapping("/{formato}/validar")
    public ResponseEntity<?> validar(@PathVariable String formato, @RequestParam int ano) {
        return ResponseEntity.ok(service.validar(ano, formato));
    }

    @Operation(summary = "Descargar archivo exógena (CSV/XML) + conservar 10 años (BNK-HU-079 E3/E5)")
    @PreAuthorize(VER)
    @GetMapping("/{formato}/export")
    public ResponseEntity<byte[]> export(@PathVariable String formato, @RequestParam int ano,
                                         @RequestParam(defaultValue = "csv") String formatoArchivo) {
        byte[] data = service.exportar(ano, formato, formatoArchivo);
        boolean xml = "xml".equalsIgnoreCase(formatoArchivo);
        String filename = "exogena_" + formato + "_" + ano + "." + (xml ? "xml" : "csv");
        String mime = xml ? "application/xml" : SimpleTableExporter.CSV_MIME;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(mime))
                .body(data);
    }

    @Operation(summary = "Histórico de generaciones de exógena (BNK-HU-079 E6)")
    @PreAuthorize(VER)
    @GetMapping("/historico/list")
    public ResponseEntity<?> historico(@RequestParam(required = false) String formato) {
        return ResponseEntity.ok(service.historico(formato));
    }
}
