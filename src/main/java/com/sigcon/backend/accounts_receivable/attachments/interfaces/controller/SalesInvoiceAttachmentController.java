package com.sigcon.backend.accounts_receivable.attachments.interfaces.controller;

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

import com.sigcon.backend.accounts_receivable.attachments.domain.model.SalesInvoiceAttachment;
import com.sigcon.backend.accounts_receivable.attachments.domain.service.SalesInvoiceAttachmentService;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * AR-03: Controlador REST para gestion de comprobantes (PDF/JPG/PNG)
 * adjuntos a facturas de venta.
 */
@RestController
@RequestMapping("/api/v1/ar")
@RequiredArgsConstructor
@Tag(name = "7. Cuentas por Cobrar - Comprobantes",
     description = "Endpoints para adjuntar comprobantes a facturas de venta")
public class SalesInvoiceAttachmentController {

    private final SalesInvoiceAttachmentService service;

    /**
     * AR-03: Adjunta un archivo (PDF/JPG/PNG, max 5MB) a una factura de venta.
     */
    @Operation(summary = "Adjuntar comprobante a factura de venta",
               description = "Carga un archivo PDF, JPG o PNG como comprobante de la factura (max 5MB)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Adjunto cargado correctamente"),
        @ApiResponse(responseCode = "400", description = "Archivo no valido o factura no existe")
    })
    @PostMapping(value = "/invoices/{id}/attachments", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('PERM_UPDATE_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> upload(@PathVariable Long id,
                                     @RequestParam("file") MultipartFile file) {
        try {
            return service.upload(id, file);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * AR-03: Lista los comprobantes adjuntos de una factura.
     */
    @Operation(summary = "Listar comprobantes de factura de venta",
               description = "Retorna los metadatos de los comprobantes adjuntos de la factura")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado obtenido")
    })
    @GetMapping("/invoices/{id}/attachments")
    @PreAuthorize("hasAuthority('PERM_READ_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> listByInvoice(@PathVariable Long id) {
        return service.listByInvoice(id);
    }

    /**
     * AR-03: Descarga el contenido binario de un adjunto.
     */
    @Operation(summary = "Descargar comprobante",
               description = "Retorna el contenido binario del comprobante en su MIME original")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Archivo retornado"),
        @ApiResponse(responseCode = "400", description = "Adjunto no encontrado")
    })
    @GetMapping("/attachments/{id}/download")
    @PreAuthorize("hasAuthority('PERM_READ_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> download(@PathVariable Long id) {
        try {
            SalesInvoiceAttachment a = service.getForDownload(id);
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
     * AR-03: Elimina un comprobante.
     */
    @Operation(summary = "Eliminar comprobante",
               description = "Elimina logicamente un comprobante adjunto")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Adjunto eliminado"),
        @ApiResponse(responseCode = "400", description = "Adjunto no encontrado")
    })
    @DeleteMapping("/attachments/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            return service.delete(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}
