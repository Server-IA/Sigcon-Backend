package com.sigcon.backend.invoices.purchase_orders.interfaces.controller;

import java.util.Optional;

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

import com.sigcon.backend.invoices.purchase_orders.application.ApprovePurchaseOrderRequest;
import com.sigcon.backend.invoices.purchase_orders.application.CreatePurchaseOrderRequest;
import com.sigcon.backend.invoices.purchase_orders.application.RejectPurchaseOrderRequest;
import com.sigcon.backend.invoices.purchase_orders.domain.service.PurchaseOrderService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para gestion de ordenes de compra del modulo Cuentas por Pagar.
 * Provee endpoints para el ciclo de vida completo: creacion, consulta, actualizacion,
 * envio a aprobacion, aprobacion, rechazo y eliminacion.
 */
@RestController
@RequestMapping("/api/v1/ap/purchase-orders")
@RequiredArgsConstructor
@Tag(name = "6. Cuentas por Pagar - Ordenes de Compra", description = "Endpoints para gestion de ordenes de compra")
public class PurchaseOrderController {

    private final PurchaseOrderService orderService;

    /**
     * Consulta ordenes de compra con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de ordenes de compra
     */
    @Operation(summary = "Consultar ordenes de compra", description = "Lista ordenes de compra con paginacion y filtros DataTable")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de ordenes de compra")
    })
    @PostMapping("")
    @PreAuthorize("hasAuthority('PERM_READ_PURCHASE_ORDER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> searchOrders(@RequestBody(required = false) DataTableRequest request) {
        return orderService.getOrders(request);
    }

    /**
     * Crea una nueva orden de compra en estado DRAFT.
     *
     * @param request       datos de la orden y sus lineas
     * @param bindingResult resultado de validacion
     * @return orden creada o errores de validacion
     */
    @Operation(summary = "Crear orden de compra", description = "Crea una nueva orden de compra en estado borrador con sus lineas de detalle")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden de compra creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio")
    })
    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_PURCHASE_ORDER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> createOrder(@Valid @RequestBody CreatePurchaseOrderRequest request,
                                         BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try {
            return orderService.createOrder(request);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Obtiene el detalle de una orden de compra por su identificador.
     *
     * @param id identificador de la orden
     * @return datos de la orden con sus lineas de detalle
     */
    @Operation(summary = "Obtener orden de compra", description = "Retorna los datos completos de una orden incluyendo sus lineas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden de compra encontrada"),
            @ApiResponse(responseCode = "400", description = "Orden no encontrada")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_READ_PURCHASE_ORDER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getOrderById(@PathVariable Long id) {
        try {
            return orderService.getOrderById(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Actualiza una orden de compra existente (solo en estado DRAFT).
     *
     * @param id      identificador de la orden
     * @param request datos actualizados
     * @return orden actualizada o errores
     */
    @Operation(summary = "Actualizar orden de compra", description = "Actualiza una orden en estado borrador. No permite modificar ordenes en otros estados")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden actualizada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Error de validacion o estado invalido")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_PURCHASE_ORDER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> updateOrder(@PathVariable Long id,
                                         @Valid @RequestBody CreatePurchaseOrderRequest request,
                                         BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try {
            return orderService.updateOrder(id, request);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Envia una orden de compra para aprobacion (DRAFT -> PENDING).
     *
     * @param id identificador de la orden
     * @return orden con estado actualizado
     */
    @Operation(summary = "Enviar a aprobacion", description = "Cambia el estado de una orden de DRAFT a PENDING para revision")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden enviada a aprobacion"),
            @ApiResponse(responseCode = "400", description = "Estado invalido para esta operacion")
    })
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('PERM_CREATE_PURCHASE_ORDER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> submitForApproval(@PathVariable Long id) {
        try {
            return orderService.submitForApproval(id);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Aprueba una orden de compra pendiente (PENDING -> APPROVED).
     *
     * @param id      identificador de la orden
     * @param request datos opcionales de aprobacion
     * @return orden aprobada
     */
    @Operation(summary = "Aprobar orden de compra", description = "Aprueba una orden en estado pendiente. Registra el aprobador y la fecha")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden aprobada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Estado invalido para esta operacion")
    })
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('PERM_APPROVE_PURCHASE_ORDER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> approveOrder(@PathVariable Long id,
                                          @RequestBody(required = false) ApprovePurchaseOrderRequest request) {
        try {
            return orderService.approveOrder(id, request);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Rechaza una orden de compra pendiente (PENDING -> REJECTED).
     *
     * @param id      identificador de la orden
     * @param request datos del rechazo (razon obligatoria)
     * @return orden rechazada
     */
    @Operation(summary = "Rechazar orden de compra", description = "Rechaza una orden en estado pendiente. Requiere una razon de rechazo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden rechazada"),
            @ApiResponse(responseCode = "400", description = "Estado invalido o razon faltante")
    })
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('PERM_APPROVE_PURCHASE_ORDER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> rejectOrder(@PathVariable Long id,
                                         @Valid @RequestBody RejectPurchaseOrderRequest request,
                                         BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try {
            return orderService.rejectOrder(id, request);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Elimina logicamente una orden de compra (solo en estado DRAFT).
     *
     * @param id identificador de la orden
     * @return mensaje de exito o error
     */
    @Operation(summary = "Eliminar orden de compra", description = "Elimina logicamente una orden en estado borrador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden eliminada exitosamente"),
            @ApiResponse(responseCode = "400", description = "No se puede eliminar la orden")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_PURCHASE_ORDER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> deleteOrder(@PathVariable Long id) {
        try {
            return orderService.deleteOrder(id);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}
