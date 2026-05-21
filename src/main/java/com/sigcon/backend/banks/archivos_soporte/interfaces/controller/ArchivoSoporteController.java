package com.sigcon.backend.banks.archivos_soporte.interfaces.controller;

import com.sigcon.backend.banks.archivos_soporte.domain.model.ArchivoSoporte;
import com.sigcon.backend.banks.archivos_soporte.domain.service.ArchivoSoporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * BNK-HU-062 / BNK-HU-063: consulta, verificación de integridad, descarga y
 * reporte de retención de soportes conservados.
 */
@RestController
@RequestMapping("/api/v1/banks/archivos-soporte")
@RequiredArgsConstructor
@Tag(name = "BNK - Soportes conservados (HU-062/063)",
     description = "Extractos/CSV/informes con hash SHA-256 y retención 10 años")
public class ArchivoSoporteController {

    private final ArchivoSoporteService service;

    @Operation(summary = "Listar soportes de una cuenta bancaria")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/by-account/{bankAccountId}")
    public ResponseEntity<?> listByAccount(@PathVariable Long bankAccountId) {
        return ResponseEntity.ok(service.listByBankAccount(bankAccountId));
    }

    @Operation(summary = "Verificar integridad del soporte (BNK-HU-062 E4)",
               description = "Recalcula el SHA-256 del archivo almacenado y lo compara con el hash registrado al cargar.")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/{id}/verify")
    public ResponseEntity<?> verify(@PathVariable Long id) {
        return ResponseEntity.ok(service.verifyIntegrity(id));
    }

    @Operation(summary = "Descargar soporte original (BNK-HU-062 E6)",
               description = "Sirve el archivo original y registra la descarga en auditoría (EXPORTAR).")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id) {
        ArchivoSoporte a = service.getForDownloadAndAudit(id);
        MediaType mt = a.getMimeType() != null
                ? MediaType.parseMediaType(a.getMimeType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + a.getFileName() + "\"")
                .contentType(mt)
                .body(a.getFileContent());
    }

    @Operation(summary = "Reporte de retención y backup (BNK-HU-063 E6)")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/retention-report")
    public ResponseEntity<?> retentionReport() {
        return ResponseEntity.ok(service.retentionReport());
    }

    @Operation(summary = "Eliminar soporte (bloqueado antes de la retención) (BNK-HU-063 E5)")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String acta = body != null ? body.get("acta") : null;
        service.blockedDelete(id, acta);
        return ResponseEntity.ok(Map.of("success", true, "message", "Soporte eliminado tras retención vencida."));
    }
}
