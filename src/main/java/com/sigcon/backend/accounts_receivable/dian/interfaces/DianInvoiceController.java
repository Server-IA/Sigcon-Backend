package com.sigcon.backend.accounts_receivable.dian.interfaces;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.accounts_receivable.dian.service.DianPdfService;
import com.sigcon.backend.accounts_receivable.dian.service.DianSubmissionService;
import com.sigcon.backend.accounts_receivable.dian.service.DianXmlService;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para operaciones DIAN sobre facturas de venta:
 * generacion XML UBL 2.1, envio al PSE y descarga del PDF con QR.
 * Cubre HUs AR-14, AR-15 y AR-16.
 */
@RestController
@RequestMapping("/api/v1/ar/dian/invoices")
@RequiredArgsConstructor
@Tag(name = "7. Cuentas por Cobrar - Facturacion Electronica DIAN",
     description = "Endpoints para generar XML UBL 2.1, CUFE, envio al PSE y representacion grafica PDF")
public class DianInvoiceController {

    private final DianXmlService xmlService;
    private final DianSubmissionService submissionService;
    private final DianPdfService pdfService;

    @Operation(summary = "Generar XML UBL 2.1 + CUFE",
               description = "Genera el XML UBL 2.1 de la factura electronica y calcula el CUFE segun el Anexo Tecnico. AR-14.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "XML y CUFE generados"),
        @ApiResponse(responseCode = "400", description = "Error de validacion o factura no encontrada")
    })
    @PostMapping("/{id}/generate-xml")
    @PreAuthorize("hasAuthority('PERM_CREATE_DIAN_XML') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> generateXml(@PathVariable Long id) {
        try {
            return xmlService.generateXml(id);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @Operation(summary = "Enviar factura a la DIAN (simulacion PSE)",
               description = "Dispara el envio asincrono al proveedor tecnologico. AR-15.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Envio encolado"),
        @ApiResponse(responseCode = "400", description = "Factura sin XML o ya enviada")
    })
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('PERM_SUBMIT_DIAN') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> submit(@PathVariable Long id) {
        try {
            return submissionService.submit(id);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @Operation(summary = "Consultar estado del envio DIAN",
               description = "Retorna el ultimo registro de transmision para la factura")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado obtenido"),
        @ApiResponse(responseCode = "400", description = "No existe envio")
    })
    @GetMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERM_READ_DIAN') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> status(@PathVariable Long id) {
        try {
            return submissionService.getStatus(id);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @Operation(summary = "Descargar representacion grafica PDF",
               description = "Genera el PDF con datos del emisor, cliente, lineas, totales, resolucion DIAN, CUFE y codigo QR de verificacion. AR-16.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PDF generado",
                content = @io.swagger.v3.oas.annotations.media.Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
        @ApiResponse(responseCode = "400", description = "Factura no encontrada")
    })
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('PERM_READ_DIAN') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> pdf(@PathVariable Long id) {
        try {
            byte[] pdf = pdfService.generatePdf(id);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "factura-" + id + ".pdf");
            return new ResponseEntity<>(pdf, headers, 200);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}
