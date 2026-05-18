package com.sigcon.backend.banks.bnk_cash_flow.interfaces.controller;

import com.sigcon.backend.banks.bnk_cash_flow.application.CreateCashFlowProjectionDTO;
import com.sigcon.backend.banks.bnk_cash_flow.application.UpdateCashFlowProjectionDTO;
import com.sigcon.backend.banks.bnk_cash_flow.domain.service.CashFlowProjectionService;
import com.sigcon.backend.utils.DataTableRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * BNK-RF-29 / BNK-RF-30 / BNK-RF-31 / BNK-RF-32
 *
 * Controlador REST del módulo de Flujo de Caja.
 * Todos los endpoints están protegidos con ROLE_ADMIN.
 *
 * Base path: /api/v1/bnk/projections
 */
@RestController
@RequestMapping("/api/v1/bnk/projections")
@RequiredArgsConstructor
@Tag(name = "5. Módulo de Bancos - Flujo de Caja", description = "Endpoints para gestión de proyecciones de flujo de caja")
@SecurityRequirement(name = "bearerAuth")
public class CashFlowProjectionController {

    private final CashFlowProjectionService cashFlowProjectionService;

    // ─────────────────────────────────────────────────────
    // BNK-RF-29 — Crear proyección
    // ─────────────────────────────────────────────────────

    @PostMapping
    @Operation(
        summary = "Registrar proyección de flujo de caja",
        description = "Crea una nueva proyección de flujo de caja. Estado inicial: BORRADOR. "
                + "El saldo final (finalBalance) es calculado automáticamente por el sistema "
                + "como initialBalance + netFlow.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Datos de la proyección a crear",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CreateCashFlowProjectionDTO.class),
                examples = @ExampleObject(value = """
                    {
                      "name": "Proyección Q1 2026",
                      "description": "Proyección trimestral del primer cuarto de 2026",
                      "startDate": "2026-01-01",
                      "endDate": "2026-03-31",
                      "periodicity": "MENSUAL",
                      "projectionType": "NETA",
                      "initialBalance": 50000000.00,
                      "netFlow": 12000000.00,
                      "currency": "COP"
                    }
                    """)
            )
        )
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Proyección registrada correctamente"),
        @ApiResponse(responseCode = "400", description = "Error de validación (nombre duplicado, fechas inválidas, campos obligatorios)"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyAuthority('PERM_CREATE_CASH_FLOW_PROJECTION','TEMP_PERM_CREATE_CASH_FLOW_PROJECTION','TEMP_CREATE_CASH_FLOW_PROJECTION','PERM_BNK.PROYECCIONES.CREAR','TEMP_PERM_BNK.PROYECCIONES.CREAR','TEMP_BNK.PROYECCIONES.CREAR','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> store(
            @Valid @RequestBody(required = false) CreateCashFlowProjectionDTO request,
            BindingResult bindingResult) {

        return cashFlowProjectionService.create(request, bindingResult);
    }

    // ─────────────────────────────────────────────────────
    // BNK-RF-30 — Modificar proyección
    // ─────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @Operation(
        summary = "Modificar proyección de flujo de caja",
        description = "Actualiza los datos de una proyección existente. "
                + "Solo se pueden modificar proyecciones en estado BORRADOR o APROBADA. "
                + "Para proyecciones APROBADAS, el campo 'modificationReason' es obligatorio. "
                + "El saldo final (finalBalance) se recalcula automáticamente.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UpdateCashFlowProjectionDTO.class),
                examples = @ExampleObject(value = """
                    {
                      "name": "Proyección Q1 2026 - Revisada",
                      "netFlow": 15000000.00,
                      "modificationReason": "Ajuste de cifras por revisión contable"
                    }
                    """)
            )
        )
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Proyección actualizada correctamente"),
        @ApiResponse(responseCode = "400", description = "Error de validación o estado no permite modificación"),
        @ApiResponse(responseCode = "404", description = "Proyección no encontrada"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyAuthority('PERM_UPDATE_CASH_FLOW_PROJECTION','TEMP_PERM_UPDATE_CASH_FLOW_PROJECTION','TEMP_UPDATE_CASH_FLOW_PROJECTION','PERM_BNK.PROYECCIONES.EDITAR','TEMP_PERM_BNK.PROYECCIONES.EDITAR','TEMP_BNK.PROYECCIONES.EDITAR','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCashFlowProjectionDTO request,
            BindingResult bindingResult) {

        return cashFlowProjectionService.update(id, request, bindingResult);
    }

    // ─────────────────────────────────────────────────────
    // BNK-RF-31 — Eliminación lógica
    // ─────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Eliminar proyección de flujo de caja (lógica)",
        description = "Realiza la eliminación lógica de una proyección. "
                + "El registro NO es borrado físicamente de la base de datos: "
                + "se marca deletedAt = NOW() y status = INACTIVA. "
                + "No se pueden eliminar proyecciones en estado EJECUTADA."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Proyección eliminada correctamente"),
        @ApiResponse(responseCode = "400", description = "Estado no permite eliminación"),
        @ApiResponse(responseCode = "404", description = "Proyección no encontrada"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyAuthority('PERM_DELETE_CASH_FLOW_PROJECTION','TEMP_PERM_DELETE_CASH_FLOW_PROJECTION','TEMP_DELETE_CASH_FLOW_PROJECTION','PERM_BNK.PROYECCIONES.ELIMINAR','TEMP_PERM_BNK.PROYECCIONES.ELIMINAR','TEMP_BNK.PROYECCIONES.ELIMINAR','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {

        return cashFlowProjectionService.delete(id);
    }

    /**
     * BNK-RF-31 — Inactivación sin eliminación del registro.
     * Permite conservar el historial de la proyección visible en admin.
     */
    @PatchMapping("/{id}/inactivate")
    @Operation(
        summary = "Inactivar proyección de flujo de caja",
        description = "Cambia el estado de la proyección a INACTIVA sin eliminar el registro. "
                + "Útil cuando la proyección tiene dependencias o se requiere conservar el historial."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Proyección inactivada correctamente"),
        @ApiResponse(responseCode = "400", description = "La proyección ya está inactiva"),
        @ApiResponse(responseCode = "404", description = "Proyección no encontrada"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyAuthority('PERM_DELETE_CASH_FLOW_PROJECTION','TEMP_PERM_DELETE_CASH_FLOW_PROJECTION','TEMP_DELETE_CASH_FLOW_PROJECTION','PERM_BNK.PROYECCIONES.ELIMINAR','TEMP_PERM_BNK.PROYECCIONES.ELIMINAR','TEMP_BNK.PROYECCIONES.ELIMINAR','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> inactivate(@PathVariable Long id) {

        return cashFlowProjectionService.inactivate(id);
    }

    /**
     * QA HU-030/031: aprobar proyeccion BORRADOR -> APROBADA.
     */
    @PostMapping("/{id}/approve")
    @Operation(
        summary = "Aprobar proyección de flujo de caja",
        description = "HU-030/031: Cambia estado de BORRADOR a APROBADA. "
                + "Solo proyecciones APROBADAS son consideradas en reportes consolidados."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Proyección aprobada"),
        @ApiResponse(responseCode = "400", description = "Estado no permite aprobacion"),
        @ApiResponse(responseCode = "404", description = "No encontrada")
    })
    @PreAuthorize("hasAnyAuthority('PERM_APPROVE_CASH_FLOW_PROJECTION','TEMP_PERM_APPROVE_CASH_FLOW_PROJECTION','TEMP_APPROVE_CASH_FLOW_PROJECTION','PERM_BNK.PROYECCIONES.APROBAR','TEMP_PERM_BNK.PROYECCIONES.APROBAR','TEMP_BNK.PROYECCIONES.APROBAR','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        return cashFlowProjectionService.approve(id);
    }

    /**
     * QA HU-059: marcar proyeccion APROBADA -> EJECUTADA (cuando ya se realizo).
     * Estado terminal; no se puede revertir.
     */
    @PostMapping("/{id}/execute")
    @Operation(
        summary = "Marcar proyección como EJECUTADA",
        description = "HU-059: Cambia estado de APROBADA a EJECUTADA cuando el flujo "
                + "previsto ya se realizo. Estado terminal — no se puede modificar despues."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Proyección ejecutada"),
        @ApiResponse(responseCode = "400", description = "Solo proyecciones APROBADAS pueden marcarse como EJECUTADA"),
        @ApiResponse(responseCode = "404", description = "No encontrada")
    })
    // QA Bloque BH (2026-05-17): el permiso `BNK.PROYECCIONES.EJECUTAR` NO
    // existe en BD; solo `BNK.PROYECCIONES.APROBAR` (id=7604) cuya descripcion
    // literal es "Aprobar/ejecutar proyeccion". Agregamos APROBAR aqui para
    // que el mismo permiso habilite ambas acciones, alineado con la semantica
    // del catalogo de permisos. EXECUTE_* se mantiene en la lista para no
    // romper si en el futuro se separa como permiso propio.
    @PreAuthorize("hasAnyAuthority('PERM_EXECUTE_CASH_FLOW_PROJECTION','TEMP_PERM_EXECUTE_CASH_FLOW_PROJECTION','TEMP_EXECUTE_CASH_FLOW_PROJECTION','PERM_BNK.PROYECCIONES.EJECUTAR','TEMP_PERM_BNK.PROYECCIONES.EJECUTAR','TEMP_BNK.PROYECCIONES.EJECUTAR','PERM_BNK.PROYECCIONES.APROBAR','TEMP_PERM_BNK.PROYECCIONES.APROBAR','TEMP_BNK.PROYECCIONES.APROBAR','PERM_APPROVE_CASH_FLOW_PROJECTION','TEMP_PERM_APPROVE_CASH_FLOW_PROJECTION','TEMP_APPROVE_CASH_FLOW_PROJECTION','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> execute(@PathVariable Long id) {
        return cashFlowProjectionService.execute(id);
    }

    // ─────────────────────────────────────────────────────
    // BNK-RF-32 — Consultas
    // ─────────────────────────────────────────────────────

    @PostMapping("/search")
    @Operation(
        summary = "Consultar proyecciones de flujo de caja",
        description = "Consulta paginada de proyecciones con filtros dinámicos (DataTable). "
                + "Soporta filtros por: nombre, estado, tipo, moneda. "
                + "Los registros eliminados lógicamente son excluidos automáticamente.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = false,
            description = "Parámetros de paginación y filtros DataTable",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DataTableRequest.class),
                examples = @ExampleObject(value = """
                        {
                          "draw": 1,
                          "start": 0,
                          "length": 10,
                          "search": { "value": "", "regex": false },
                          "columns": [],
                          "order": []
                        }
                        """)
            )
        )
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consulta realizada correctamente"),
        @ApiResponse(responseCode = "400", description = "No se encontraron proyecciones o parámetros inválidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_CASH_FLOW_PROJECTION','TEMP_PERM_VIEW_CASH_FLOW_PROJECTION','TEMP_VIEW_CASH_FLOW_PROJECTION','PERM_BNK.PROYECCIONES.VER','TEMP_PERM_BNK.PROYECCIONES.VER','TEMP_BNK.PROYECCIONES.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest request) {

        return cashFlowProjectionService.findAllPaged(request);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Detalle de proyección de flujo de caja",
        description = "Retorna la información completa de una proyección por su ID. "
                + "Excluye registros eliminados lógicamente."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detalle obtenido correctamente"),
        @ApiResponse(responseCode = "404", description = "Proyección no encontrada o eliminada"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_CASH_FLOW_PROJECTION','TEMP_PERM_VIEW_CASH_FLOW_PROJECTION','TEMP_VIEW_CASH_FLOW_PROJECTION','PERM_BNK.PROYECCIONES.VER','TEMP_PERM_BNK.PROYECCIONES.VER','TEMP_BNK.PROYECCIONES.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> detail(@PathVariable Long id) {

        return cashFlowProjectionService.getDetail(id);
    }
}
