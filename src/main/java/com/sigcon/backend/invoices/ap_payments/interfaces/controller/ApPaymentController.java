package com.sigcon.backend.invoices.ap_payments.interfaces.controller;

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

import com.sigcon.backend.invoices.ap_payments.application.CreateApPaymentRequest;
import com.sigcon.backend.invoices.ap_payments.domain.service.ApPaymentService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para gestion de pagos y abonos a facturas de compra.
 * Provee endpoints para registrar pagos, consultar pagos y listar pagos por factura.
 */
@RestController
@RequestMapping("/api/v1/ap/payments")
@RequiredArgsConstructor
@Tag(name = "6. Cuentas por Pagar - Pagos y Abonos", description = "Endpoints para registro y consulta de pagos a facturas de compra")
public class ApPaymentController {

    private final ApPaymentService paymentService;

    /**
     * Consulta pagos con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de pagos
     */
    @Operation(summary = "Consultar pagos", description = "Lista pagos con paginacion y filtros DataTable")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de pagos")
    })
    @PostMapping("")
    @PreAuthorize("hasAuthority('PERM_READ_AP_PAYMENT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> searchPayments(@RequestBody(required = false) DataTableRequest request) {
        return paymentService.getPayments(request);
    }

    /**
     * Registra un nuevo pago o abono a una factura de compra.
     *
     * @param request       datos del pago
     * @param bindingResult resultado de validacion
     * @return pago registrado o errores de validacion
     */
    @Operation(summary = "Registrar pago", description = "Registra un pago o abono a una factura de compra. Actualiza el saldo pendiente y genera asiento contable")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago registrado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio")
    })
    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_AP_PAYMENT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> registerPayment(@Valid @RequestBody CreateApPaymentRequest request,
                                             BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try {
            return paymentService.registerPayment(request);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Obtiene todos los pagos asociados a una factura especifica.
     *
     * @param invoiceId identificador de la factura
     * @return lista de pagos de la factura
     */
    @Operation(summary = "Pagos por factura", description = "Obtiene todos los pagos registrados para una factura especifica")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de pagos de la factura")
    })
    @GetMapping("/invoice/{invoiceId}")
    @PreAuthorize("hasAuthority('PERM_READ_AP_PAYMENT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getPaymentsByInvoice(@PathVariable Long invoiceId) {
        return paymentService.getPaymentsByInvoice(invoiceId);
    }
}
