package com.sigcon.backend.invoices.ap_payments.interfaces.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.invoices.ap_payments.application.ApplyAdvanceRequest;
import com.sigcon.backend.invoices.ap_payments.application.CreateApAdvanceRequest;
import com.sigcon.backend.invoices.ap_payments.domain.service.ApAdvanceService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para gestion de anticipos a proveedores.
 * Provee endpoints para registrar anticipos, consultarlos y aplicarlos a facturas.
 */
@RestController
@RequestMapping("/api/v1/ap/advances")
@RequiredArgsConstructor
@Tag(name = "6. Cuentas por Pagar - Anticipos", description = "Endpoints para registro, consulta y aplicacion de anticipos a proveedores")
public class ApAdvanceController {

    private final ApAdvanceService advanceService;

    /**
     * Consulta anticipos con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de anticipos
     */
    @Operation(summary = "Consultar anticipos", description = "Lista anticipos con paginacion y filtros DataTable")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de anticipos")
    })
    @PostMapping("")
    @PreAuthorize("hasAuthority('PERM_READ_AP_ADVANCE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> searchAdvances(@RequestBody(required = false) DataTableRequest request) {
        return advanceService.getAdvances(request);
    }

    /**
     * Registra un nuevo anticipo a un proveedor.
     *
     * @param request       datos del anticipo
     * @param bindingResult resultado de validacion
     * @return anticipo registrado o errores de validacion
     */
    @Operation(summary = "Registrar anticipo", description = "Registra un anticipo a un proveedor. Genera asiento contable automaticamente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Anticipo registrado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio")
    })
    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_AP_ADVANCE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> registerAdvance(@Valid @RequestBody CreateApAdvanceRequest request,
                                             BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try {
            return advanceService.registerAdvance(request);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Aplica un anticipo existente a una factura de compra.
     *
     * @param id            identificador del anticipo
     * @param request       datos de la aplicacion
     * @param bindingResult resultado de validacion
     * @return anticipo actualizado o errores
     */
    @Operation(summary = "Aplicar anticipo", description = "Aplica un anticipo pendiente a una factura de compra del mismo tercero")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Anticipo aplicado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio")
    })
    @PostMapping("/{id}/apply")
    @PreAuthorize("hasAuthority('PERM_CREATE_AP_ADVANCE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> applyAdvance(@PathVariable Long id,
                                          @Valid @RequestBody ApplyAdvanceRequest request,
                                          BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try {
            return advanceService.applyAdvance(id, request);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}
