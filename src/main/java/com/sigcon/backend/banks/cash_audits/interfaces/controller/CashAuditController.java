package com.sigcon.backend.banks.cash_audits.interfaces.controller;

import com.sigcon.backend.banks.cash_audits.application.ApproveCashAuditRequest;
import com.sigcon.backend.banks.cash_audits.application.CreateCashAuditRequest;
import com.sigcon.backend.banks.cash_audits.domain.service.CashAuditService;
import com.sigcon.backend.utils.DataTableRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * BNK-RF-17 a BNK-RF-20 - Controlador REST para Arqueos de Caja.
 *
 * Permite registrar, aprobar, cerrar y consultar arqueos de caja.
 * Todos los endpoints requieren ROLE_ADMIN.
 *
 * Base path: /api/v1/cash-audits
 */
@RestController
@RequestMapping("/api/v1/cash-audits")
@RequiredArgsConstructor
@Tag(name = "5. Modulo de Bancos y Cajas - Arqueos de Caja",
     description = "Endpoints para gestion de arqueos de caja")
@SecurityRequirement(name = "bearerAuth")
public class CashAuditController {

    private final CashAuditService cashAuditService;

    /**
     * Consulta paginada de arqueos de caja (DataTable).
     */
    @PostMapping("")
    @Operation(summary = "Consultar arqueos de caja",
               description = "Retorna listado paginado de arqueos de caja con filtros dinamicos.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consulta realizada correctamente"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest request) {
        return cashAuditService.search(request);
    }

    /**
     * Registra un nuevo arqueo de caja.
     */
    @PostMapping("/store")
    @Operation(summary = "Registrar arqueo de caja",
               description = "Crea un nuevo arqueo de caja. El saldo del sistema se obtiene automaticamente. "
                       + "Estado inicial: ABIERTO.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Arqueo registrado correctamente"),
        @ApiResponse(responseCode = "400", description = "Caja no encontrada o inactiva"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> store(@Valid @RequestBody CreateCashAuditRequest request) {
        return cashAuditService.create(request);
    }

    /**
     * Aprueba un arqueo de caja y genera asiento de ajuste si hay diferencia.
     */
    @PostMapping("/{id}/approve")
    @Operation(summary = "Aprobar arqueo de caja",
               description = "Cambia el estado del arqueo a APROBADO. Si existe diferencia entre saldo "
                       + "fisico y del sistema, genera un asiento contable de ajuste.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Arqueo aprobado correctamente"),
        @ApiResponse(responseCode = "400", description = "Arqueo no encontrado o estado invalido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> approve(@PathVariable Long id,
                                      @RequestBody(required = false) ApproveCashAuditRequest request) {
        return cashAuditService.approve(id, request);
    }

    /**
     * Cierra un arqueo de caja previamente aprobado.
     */
    @PostMapping("/{id}/close")
    @Operation(summary = "Cerrar arqueo de caja",
               description = "Cambia el estado del arqueo a CERRADO. Solo se puede cerrar un arqueo APROBADO.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Arqueo cerrado correctamente"),
        @ApiResponse(responseCode = "400", description = "Arqueo no encontrado o estado invalido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> close(@PathVariable Long id) {
        return cashAuditService.close(id);
    }

    /**
     * Obtiene el detalle de un arqueo de caja por su ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Detalle de arqueo de caja",
               description = "Retorna la informacion completa de un arqueo por su ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detalle obtenido correctamente"),
        @ApiResponse(responseCode = "400", description = "Arqueo no encontrado"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return cashAuditService.getById(id);
    }
}
