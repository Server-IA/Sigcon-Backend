package com.sigcon.backend.accounts_receivable.payments.interfaces.controller;

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

import com.sigcon.backend.accounts_receivable.payments.application.CreateArPaymentRequest;
import com.sigcon.backend.accounts_receivable.payments.domain.service.ArPaymentService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para gestion de cobros y abonos a facturas de venta.
 * Cubre HUs AR-02 y AR-08.
 * Provee endpoints para registrar cobros, consultar cobros, buscar por id
 * y listar cobros por factura.
 */
@RestController
@RequestMapping("/api/v1/ar/payments")
@RequiredArgsConstructor
@Tag(name = "7. Cuentas por Cobrar - Operaciones",
     description = "Endpoints para registro y consulta de cobros, anticipos y notas de facturas de venta")
public class ArPaymentController {

    private final ArPaymentService paymentService;

    /**
     * Registra un nuevo cobro o abono a una factura de venta.
     *
     * @param request       datos del cobro
     * @param bindingResult resultado de validacion
     * @return cobro registrado o errores de validacion
     */
    @Operation(summary = "Registrar cobro",
               description = "Registra un cobro o abono a una factura de venta. Actualiza el saldo pendiente y genera asiento contable (Debito Bancos / Credito CxC cliente)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cobro registrado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio")
    })
    @PostMapping("")
    @PreAuthorize("hasAuthority('PERM_CREATE_AR_PAYMENT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> registerPayment(@Valid @RequestBody CreateArPaymentRequest request,
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
     * Consulta cobros con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de cobros
     */
    @Operation(summary = "Buscar cobros", description = "Lista cobros con paginacion y filtros DataTable")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de cobros")
    })
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_READ_AR_PAYMENT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> searchPayments(@RequestBody(required = false) DataTableRequest request) {
        return paymentService.getPayments(request);
    }

    /**
     * Obtiene un cobro por su identificador.
     *
     * @param id identificador del cobro
     * @return cobro encontrado
     */
    @Operation(summary = "Obtener cobro por id", description = "Obtiene un cobro especifico por su identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cobro encontrado"),
            @ApiResponse(responseCode = "400", description = "Cobro no encontrado")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_READ_AR_PAYMENT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return paymentService.getById(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Obtiene todos los cobros asociados a una factura especifica.
     *
     * @param invoiceId identificador de la factura
     * @return lista de cobros de la factura
     */
    @Operation(summary = "Cobros por factura", description = "Obtiene todos los cobros registrados para una factura de venta")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de cobros de la factura")
    })
    @GetMapping("/invoice/{invoiceId}")
    @PreAuthorize("hasAuthority('PERM_READ_AR_PAYMENT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getPaymentsByInvoice(@PathVariable Long invoiceId) {
        return paymentService.getPaymentsByInvoice(invoiceId);
    }
}
