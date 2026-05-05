package com.sigcon.backend.accounts_receivable.advances.interfaces.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.accounts_receivable.advances.application.ApplyArAdvanceRequest;
import com.sigcon.backend.accounts_receivable.advances.application.CreateArAdvanceRequest;
import com.sigcon.backend.accounts_receivable.advances.domain.service.ArAdvanceService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para gestion de anticipos de clientes.
 * Cubre HU AR-09.
 * Provee endpoints para registrar anticipos, buscarlos y aplicarlos a facturas de venta.
 */
@RestController
@RequestMapping("/api/v1/ar/advances")
@RequiredArgsConstructor
@Tag(name = "7. Cuentas por Cobrar - Operaciones",
     description = "Endpoints para registro y consulta de cobros, anticipos y notas de facturas de venta")
public class ArAdvanceController {

    private final ArAdvanceService advanceService;

    /**
     * Registra un nuevo anticipo recibido de un cliente.
     *
     * @param request       datos del anticipo
     * @param bindingResult resultado de validacion
     * @return anticipo registrado o errores de validacion
     */
    @Operation(summary = "Registrar anticipo de cliente",
               description = "Registra un anticipo recibido de un cliente. Genera asiento contable automaticamente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Anticipo registrado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio")
    })
    @PostMapping("")
    @PreAuthorize("hasAuthority('PERM_CREATE_AR_ADVANCE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> registerAdvance(@Valid @RequestBody CreateArAdvanceRequest request,
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
     * Consulta anticipos con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de anticipos
     */
    @Operation(summary = "Buscar anticipos", description = "Lista anticipos con paginacion y filtros DataTable")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de anticipos")
    })
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_READ_AR_ADVANCE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> searchAdvances(@RequestBody(required = false) DataTableRequest request) {
        return advanceService.getAdvances(request);
    }

    /**
     * Aplica un anticipo a una factura de venta del mismo tercero.
     *
     * @param id            identificador del anticipo
     * @param request       datos de la aplicacion
     * @param bindingResult resultado de validacion
     * @return anticipo actualizado o errores
     */
    @Operation(summary = "Aplicar anticipo a factura",
               description = "Aplica un anticipo parcial o totalmente a una factura de venta del mismo tercero. Genera asiento contable (Debito Anticipos clientes / Credito CxC)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Anticipo aplicado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio")
    })
    @PostMapping("/{id}/apply")
    @PreAuthorize("hasAuthority('PERM_CREATE_AR_ADVANCE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> applyAdvance(@PathVariable Long id,
                                          @Valid @RequestBody ApplyArAdvanceRequest request,
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
