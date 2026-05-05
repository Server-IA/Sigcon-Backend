package com.sigcon.backend.general.accounting.journal.attachments.interfaces.controller;

import java.util.Optional;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sigcon.backend.general.accounting.journal.attachments.domain.model.JournalEntrySupport;
import com.sigcon.backend.general.accounting.journal.attachments.domain.service.JournalEntrySupportService;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * HU-CG-05A/B/C: controlador REST para soportes documentales (PDF/JPG/PNG)
 * adjuntos a comprobantes contables (JournalEntry).
 *
 * <p>Endpoints expuestos bajo `/api/v1/journal-entries/...`:</p>
 * <ul>
 *   <li>POST  /{id}/supports - subir soporte (multipart)</li>
 *   <li>GET   /{id}/supports - listar soportes</li>
 *   <li>GET   /supports/{id}/download - descargar binario</li>
 *   <li>DELETE /supports/{id} - eliminar (soft delete)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/journal-entries")
@RequiredArgsConstructor
@Tag(name = "9. Contabilidad General - Soportes",
     description = "HU-CG-05A/B/C: Adjuntar comprobantes (PDF/JPG/PNG) a asientos contables")
public class JournalEntrySupportController {

    private final JournalEntrySupportService service;

    /** HU-CG-05A: adjunta archivo (PDF/JPG/PNG, max 5MB) a un asiento contable. */
    @Operation(summary = "Adjuntar soporte a comprobante contable",
               description = "HU-CG-05A: carga un archivo PDF, JPG o PNG (max 5MB) como "
                           + "soporte de un asiento contable. Acepta supportType (FACTURA, "
                           + "RECIBO, CONTRATO, OTRO) y description opcionales.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Soporte cargado correctamente"),
        @ApiResponse(responseCode = "400", description = "Archivo no valido, tipo MIME no permitido o tamaño excedido")
    })
    @PostMapping(value = "/{id}/supports", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('PERM_CG.COMPROBANTES.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> upload(@PathVariable Long id,
                                     @RequestParam("file") MultipartFile file,
                                     @Parameter(description = "Categoria libre del soporte: FACTURA, RECIBO, CONTRATO, OTRO")
                                     @RequestParam(value = "supportType", required = false) String supportType,
                                     @Parameter(description = "Descripcion opcional del adjunto")
                                     @RequestParam(value = "description", required = false) String description) {
        try {
            return service.upload(id, file, supportType, description);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /** HU-CG-05C: lista los soportes vigentes de un comprobante. */
    @Operation(summary = "Listar soportes de comprobante contable",
               description = "HU-CG-05C: retorna metadatos (sin contenido binario) de los "
                           + "soportes adjuntos del asiento, ordenados por fecha de carga DESC.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado obtenido")
    })
    @GetMapping("/{id}/supports")
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> listByJournalEntry(@PathVariable Long id) {
        return service.listByJournalEntry(id);
    }

    /** HU-CG-05C: descarga el contenido binario de un soporte. */
    @Operation(summary = "Descargar soporte",
               description = "HU-CG-05C: retorna el contenido binario del soporte en su MIME original "
                           + "(application/pdf, image/jpeg, image/png).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Archivo retornado"),
        @ApiResponse(responseCode = "400", description = "Soporte no encontrado")
    })
    @GetMapping("/supports/{id}/download")
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> download(@PathVariable Long id) {
        try {
            JournalEntrySupport a = service.getForDownload(id);
            ByteArrayResource resource = new ByteArrayResource(a.getFileContent());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(a.getMimeType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + a.getFileName() + "\"")
                    .contentLength(a.getFileSize())
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /** HU-CG-05A: elimina logicamente un soporte. */
    @Operation(summary = "Eliminar soporte",
               description = "HU-CG-05A: elimina logicamente un soporte adjunto. El asiento "
                           + "no se modifica.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Soporte eliminado"),
        @ApiResponse(responseCode = "400", description = "Soporte no encontrado")
    })
    @DeleteMapping("/supports/{id}")
    @PreAuthorize("hasAuthority('PERM_CG.COMPROBANTES.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            return service.delete(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}
