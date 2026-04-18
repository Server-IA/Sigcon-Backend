package com.sigcon.backend.invoices.purchase_orders.interfaces.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.invoices.purchase_orders.application.CreateGoodsReceiptRequest;
import com.sigcon.backend.invoices.purchase_orders.application.LinkInvoiceRequest;
import com.sigcon.backend.invoices.purchase_orders.application.RejectGoodsReceiptRequest;
import com.sigcon.backend.invoices.purchase_orders.domain.service.GoodsReceiptService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para gestion de recepciones de bienes/servicios.
 * Permite registrar recepciones, consultarlas y vincularlas con facturas
 * de compra para el three-way match (OC - Recepcion - Factura).
 */
@RestController
@RequestMapping("/api/v1/ap/receipts")
@RequiredArgsConstructor
@Tag(name = "6. Cuentas por Pagar - Recepciones", description = "Endpoints para gestion de recepciones de bienes y servicios")
public class GoodsReceiptController {

    private final GoodsReceiptService receiptService;

    /**
     * Consulta recepciones con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de recepciones
     */
    @Operation(summary = "Consultar recepciones", description = "Lista recepciones de bienes con paginacion y filtros DataTable")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de recepciones")
    })
    @PostMapping("")
    @PreAuthorize("hasAuthority('PERM_READ_GOODS_RECEIPT') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> searchReceipts(@RequestBody(required = false) DataTableRequest request) {
        return receiptService.getReceipts(request);
    }

    /**
     * Registra una nueva recepcion de bienes/servicios.
     *
     * @param request       datos de la recepcion y sus lineas
     * @param bindingResult resultado de validacion
     * @return recepcion registrada o errores de validacion
     */
    @Operation(summary = "Registrar recepcion", description = "Registra una recepcion de bienes para una orden de compra aprobada. Valida cantidades pendientes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recepcion registrada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio")
    })
    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_GOODS_RECEIPT') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createReceipt(@Valid @RequestBody CreateGoodsReceiptRequest request,
                                           BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try {
            return receiptService.createReceipt(request);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Obtiene el detalle de una recepcion por su identificador.
     *
     * @param id identificador de la recepcion
     * @return datos de la recepcion con sus lineas
     */
    @Operation(summary = "Obtener recepcion", description = "Retorna los datos completos de una recepcion incluyendo sus lineas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recepcion encontrada"),
            @ApiResponse(responseCode = "400", description = "Recepcion no encontrada")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_READ_GOODS_RECEIPT') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getReceiptById(@PathVariable Long id) {
        try {
            return receiptService.getReceiptById(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Vincula una factura de compra a una recepcion (three-way match).
     *
     * @param id      identificador de la recepcion
     * @param request datos con el ID de la factura
     * @return recepcion actualizada con factura vinculada
     */
    @Operation(summary = "Vincular factura", description = "Asocia una factura de compra a una recepcion para completar el three-way match")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Factura vinculada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Error de validacion o la recepcion ya tiene factura")
    })
    @PostMapping("/{id}/link-invoice")
    @PreAuthorize("hasAuthority('PERM_UPDATE_GOODS_RECEIPT') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> linkInvoice(@PathVariable Long id,
                                         @Valid @RequestBody LinkInvoiceRequest request,
                                         BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try {
            return receiptService.linkToInvoice(id, request);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * AP-22: Rechaza o registra devolucion de una recepcion con motivo obligatorio.
     *
     * @param id      identificador de la recepcion a rechazar
     * @param request request con el motivo (min 20 chars)
     * @return recepcion marcada como REJECTED con auditores
     */
    @Operation(
        summary = "Rechazar/devolver recepcion",
        description = "Marca una recepcion como REJECTED conservando el historial. "
                    + "No permitido si ya esta vinculada a factura o ya rechazada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recepcion rechazada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Validacion fallida o regla de negocio"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('PERM_UPDATE_GOODS_RECEIPT') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> rejectReceipt(@PathVariable Long id,
                                           @Valid @RequestBody RejectGoodsReceiptRequest request,
                                           BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try {
            return receiptService.rejectReceipt(id, request);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}
