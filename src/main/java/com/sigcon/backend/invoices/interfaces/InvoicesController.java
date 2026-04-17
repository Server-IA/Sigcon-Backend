package com.sigcon.backend.invoices.interfaces;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.invoices.application.BulkImportRequest;
import com.sigcon.backend.invoices.application.InvoiceFCRequestDTO;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.model.TypesInvoices;
import com.sigcon.backend.invoices.domain.repository.TypeInvoiceRepository;
import com.sigcon.backend.invoices.domain.service.InvoiceService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para gestion de facturas del modulo Cuentas por Pagar (AP).
 * Provee endpoints CRUD completos para facturas de compra.
 */
@RequestMapping("/api/v1/invoices")
@RestController
@RequiredArgsConstructor
@Tag(name = "6. Cuentas por Pagar - Facturas", description = "Endpoints para gestion de facturas de compra")
public class InvoicesController {

    private final InvoiceService invoicesService;
    /**
     * Repositorio para resolver {@code TypesInvoices} por codigo ("FC", "FV", etc.).
     * Necesario porque el orden de seeds (V24 antes que V3-1) hace que el id
     * numerico de FC no sea constante entre entornos.
     */
    private final TypeInvoiceRepository typeInvoiceRepository;

    /**
     * Crea una nueva factura de compra (FC).
     *
     * @param invoiceFCRequestDTO datos de la factura
     * @param bindingResult       resultado de validacion
     * @return factura creada o errores de validacion
     */
    @Operation(summary = "Crear factura FC", description = "Crea una nueva factura de compra con sus lineas de detalle e impuestos")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Factura creada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio")
    })
    @PostMapping("/fc")
    @PreAuthorize("hasAuthority('PERM_CREATE_INVOICE_FC') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createInvoiceFC(
        @jakarta.validation.Valid @RequestBody InvoiceFCRequestDTO invoiceFCRequestDTO,
        BindingResult bindingResult
    ) {
        if(bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try {
            // Resolver el id del tipo FC por codigo. NO hardcodear 1L: el orden de seeds
            // (V24 antes que V3-1 en orden alfabetico) hace que NC pueda obtener id=1 y FC
            // id=3 o similar, segun el entorno.
            TypesInvoices fcType = typeInvoiceRepository.findByCodeAndDeletedAtIsNull("FC")
                .orElseThrow(() -> new IllegalStateException(
                    "Tipo de factura 'FC' (Factura de compra) no esta configurado en el catalogo types_invoices"));
            // Devolver el DTO (no la entidad raw): evita referencias ciclicas,
            // proxies Hibernate (ByteBuddyInterceptor) y lazy loading al serializar.
            return invoicesService.createInvoiceAndReturnDto(invoiceFCRequestDTO, fcType.getId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(ErrorRespondJson.getErrorRespondMessage(java.util.Optional.of(e.getMessage())));
        }
    }

    /**
     * Consulta facturas con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de facturas
     */
    @Operation(summary = "Consultar facturas", description = "Lista facturas con paginacion y filtros DataTable")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado de facturas")
    })
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_READ_INVOICE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getInvoices(@RequestBody(required = false) DataTableRequest request) {
        return invoicesService.getInvoices(request);
    }

    /**
     * Obtiene una factura por su identificador.
     *
     * @param id identificador de la factura
     * @return datos de la factura con sus lineas de detalle
     */
    @Operation(summary = "Obtener factura por ID", description = "Retorna los datos completos de una factura incluyendo sus lineas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Factura encontrada"),
        @ApiResponse(responseCode = "404", description = "Factura no encontrada")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_READ_INVOICE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getInvoiceById(@PathVariable Long id) {
        try {
            return invoicesService.getInvoiceById(id);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                .body(ErrorRespondJson.getErrorRespondMessage(java.util.Optional.of(e.getMessage())));
        }
    }

    /**
     * Actualiza una factura existente.
     *
     * @param id      identificador de la factura
     * @param request datos a actualizar
     * @return factura actualizada o errores de validacion
     */
    @Operation(summary = "Actualizar factura", description = "Actualiza los datos de una factura existente. No permite modificar facturas anuladas o liquidadas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Factura actualizada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio"),
        @ApiResponse(responseCode = "404", description = "Factura no encontrada")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_INVOICE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateInvoice(@PathVariable Long id, @RequestBody InvoiceFCRequestDTO request) {
        try {
            return invoicesService.updateInvoice(id, request);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(ErrorRespondJson.getErrorRespondMessage(java.util.Optional.of(e.getMessage())));
        }
    }

    /**
     * Elimina logicamente una factura (soft delete).
     * Solo permite eliminar facturas en estado PENDING.
     *
     * @param id identificador de la factura
     * @return mensaje de exito o error
     */
    @Operation(summary = "Eliminar factura", description = "Elimina logicamente una factura. Solo facturas en estado PENDING pueden ser eliminadas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Factura eliminada exitosamente"),
        @ApiResponse(responseCode = "400", description = "No se puede eliminar la factura")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_INVOICE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deleteInvoice(@PathVariable Long id) {
        try {
            return invoicesService.deleteInvoice(id);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(ErrorRespondJson.getErrorRespondMessage(java.util.Optional.of(e.getMessage())));
        }
    }

    /**
     * AP-03: Liquida una factura cuando su saldo pendiente es cero.
     */
    @io.swagger.v3.oas.annotations.Operation(summary = "Liquidar factura",
        description = "AP-03: Marca una factura como LIQUIDADA cuando su saldo pendiente es cero.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Factura liquidada exitosamente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "La factura tiene saldo pendiente o estado no permite liquidacion"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Factura no encontrada")
    })
    @PostMapping("/{id}/settle")
    @PreAuthorize("hasAuthority('PERM_UPDATE_INVOICE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> settleInvoice(@PathVariable Long id) {
        try {
            return invoicesService.settleInvoice(id);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(ErrorRespondJson.getErrorRespondMessage(java.util.Optional.of(e.getMessage())));
        }
    }

    /**
     * Importa facturas de compra de forma masiva desde un archivo CSV en Base64.
     * Cada fila debe contener: thirdPartyId, paymentFormId, resolutionInvoice, invoiceDate, invoiceDueDay.
     *
     * @param request datos del archivo y delimitador
     * @return resumen de importacion con conteo de exitos y errores
     */
    @Operation(summary = "Importacion masiva de facturas", description = "Importa facturas desde archivo CSV codificado en Base64. Procesa cada fila y reporta errores individuales")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Importacion completada (ver resumen)"),
        @ApiResponse(responseCode = "400", description = "Error en el archivo o formato invalido")
    })
    @PostMapping("/bulk/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_INVOICE_FC') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> bulkImportInvoices(@RequestBody BulkImportRequest request) {
        try {
            return invoicesService.bulkImportInvoices(request.getFileBase64(), request.getDelimiter());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(ErrorRespondJson.getErrorRespondMessage(java.util.Optional.of(e.getMessage())));
        }
    }

}
