package com.sigcon.backend.invoices.attachments.interfaces.controller;

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

import com.sigcon.backend.invoices.attachments.domain.model.InvoiceAttachment;
import com.sigcon.backend.invoices.attachments.domain.service.InvoiceAttachmentService;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * AP-13: Controlador REST para gestion de documentos soporte (PDF/JPG/PNG)
 * adjuntos a facturas de compra. Soporta clasificacion por tipo:
 * PURCHASE_ORDER, RECEPTION_ACT, CONTRACT, OTHER.
 */
@RestController
@RequestMapping("/api/v1/ap")
@RequiredArgsConstructor
@Tag(name = "6. Cuentas por Pagar - Documentos Soporte",
     description = "Endpoints para adjuntar documentos soporte (OC, acta, contrato) a facturas de compra")
public class InvoiceAttachmentController {

    private final InvoiceAttachmentService service;

    /**
     * AP-13: Adjunta un documento soporte (PDF/JPG/PNG, max 5MB) a una factura de compra.
     *
     * @param id           ID de la factura
     * @param file         archivo multipart
     * @param documentType tipo del documento (PURCHASE_ORDER/RECEPTION_ACT/CONTRACT/OTHER)
     * @param description  descripcion opcional
     */
    @Operation(summary = "Adjuntar documento soporte a factura de compra",
               description = "Carga un archivo PDF, JPG o PNG como documento soporte de la factura (max 5MB). "
                           + "documentType: PURCHASE_ORDER, RECEPTION_ACT, CONTRACT, OTHER.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Documento cargado correctamente"),
        @ApiResponse(responseCode = "400", description = "Archivo no valido, tipo no permitido o factura no existe")
    })
    @PostMapping(value = "/invoices/{id}/attachments", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('PERM_UPDATE_AP_INVOICE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> upload(@PathVariable Long id,
                                     @RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "documentType", required = false) String documentType,
                                     @RequestParam(value = "description", required = false) String description) {
        try {
            return service.upload(id, file, documentType, description);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * AP-13: Lista documentos soporte de una factura. Puede filtrar por tipo.
     */
    @Operation(summary = "Listar documentos soporte de factura de compra",
               description = "Retorna los metadatos de los documentos soporte de la factura. "
                           + "Filtro opcional por documentType.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado obtenido")
    })
    @GetMapping("/invoices/{id}/attachments")
    @PreAuthorize("hasAuthority('PERM_READ_AP_INVOICE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listByInvoice(@PathVariable Long id,
                                            @RequestParam(value = "documentType", required = false) String documentType) {
        return service.listByInvoice(id, documentType);
    }

    /**
     * AP-13: Descarga el contenido binario de un documento soporte.
     */
    @Operation(summary = "Descargar documento soporte",
               description = "Retorna el contenido binario del documento en su MIME original")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Archivo retornado"),
        @ApiResponse(responseCode = "400", description = "Adjunto no encontrado")
    })
    @GetMapping("/attachments/{id}/download")
    @PreAuthorize("hasAuthority('PERM_READ_AP_INVOICE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> download(@PathVariable Long id) {
        try {
            InvoiceAttachment a = service.getForDownload(id);
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

    /**
     * AP-13: Elimina logicamente un documento soporte.
     */
    @Operation(summary = "Eliminar documento soporte",
               description = "Elimina logicamente un documento soporte adjunto")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Adjunto eliminado"),
        @ApiResponse(responseCode = "400", description = "Adjunto no encontrado")
    })
    @DeleteMapping("/attachments/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_AP_INVOICE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            return service.delete(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}
