package com.sigcon.backend.banks.cash_management.interfaces;


import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.banks.cash_management.application.CashDTO;
import com.sigcon.backend.banks.cash_management.application.ChangeCashStatusRequest;
import com.sigcon.backend.banks.cash_management.application.CreateCashRequest;
import com.sigcon.backend.banks.cash_management.application.UpdateCashRequest;
import com.sigcon.backend.banks.cash_management.domain.service.CashService;
import com.sigcon.backend.utils.DataTableRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/v1/cash")
@RequiredArgsConstructor
@Tag(name = "5. Módulo de Bancos - Cajas", description = "Gestión del ciclo de vida de cajas: creación, edición, eliminación, cambio de estado y consulta.")
public class CashController {

     private final CashService cashService;

    /**
     * BNK-RF-10 — Crear caja.
     * POST /api/v1/cash/store
     */
    @Operation(
        summary = "Crear una caja de efectivo",
        description = "BNK-RF-10 — Registra una nueva caja de efectivo con todos sus datos de identificación, " +
                      "ubicación, responsables, datos financieros, límites y configuración contable. " +
                      "El estado se asigna automáticamente como ACTIVE y el saldo actual se inicializa " +
                      "igual al saldo inicial. Requiere permiso PERM_CREATE_CASH.", 
                       requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CreateCashRequest.class)
            )
        )
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Caja creada exitosamente", content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = CashDTO.class))),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o reglas de negocio incumplidas"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Sin permiso PERM_CREATE_CASH")
    })
    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_CASH') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> store(
            @Valid @RequestBody CreateCashRequest request,
            BindingResult bindingResult) {
        return cashService.createCash(request, bindingResult);
    }

    /**
     * BNK-RF-10 — Editar caja.
     * PUT /api/v1/cash/update/{id}
     */
    @Operation(
        summary = "Actualizar una caja de efectivo",
        description = "BNK-RF-10 — Modifica los datos de una caja de efectivo existente. " +
                      "Para cambios sensibles en cajas con movimientos registrados, " +
                      "requiere el campo changeReason con mínimo 10 caracteres. " +
                      "Requiere permiso PERM_UPDATE_CASH.", 
                       requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UpdateCashRequest.class)
            )
        )
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Caja actualizada exitosamente", content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = CashDTO.class))),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o reglas de negocio incumplidas"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Sin permiso PERM_UPDATE_CASH"),
        @ApiResponse(responseCode = "404", description = "Caja no encontrada")
    })
    @PutMapping("/update/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_CASH') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCashRequest request,
            BindingResult bindingResult) {
        return cashService.updateCash(id, request, bindingResult);
    }

    /**
     * BNK-RF-11 — Eliminar o desactivar caja.
     * DELETE /api/v1/cash/delete/{id}
     */
    @Operation(
        summary = "Eliminar o desactivar una caja de efectivo",
        description = "BNK-RF-11 — Si la caja no tiene dependencias (movimientos, arqueos ni referencias contables) " +
                      "se elimina físicamente previa confirmación reforzada con la palabra clave 'ELIMINAR'. " +
                      "Si tiene dependencias, se desactiva (estado INACTIVE) conservando el historial. " +
                      "El motivo es obligatorio con mínimo 40 caracteres. " +
                      "Requiere rol ADMIN para eliminación física, permiso PERM_DELETE_CASH para desactivación."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Caja eliminada o desactivada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Confirmación reforzada fallida o motivo insuficiente"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Sin permisos suficientes"),
        @ApiResponse(responseCode = "404", description = "Caja no encontrada")
    })
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_CASH') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            @RequestParam String confirmation,
            @RequestParam String reason) {
        return cashService.deleteCash(id, confirmation, reason);
    }

    /**
     * BNK-RF-12 — Cambiar estado de caja.
     * PUT /api/v1/cash/{id}/status
     */
    @Operation(
        summary = "Cambiar el estado de una caja de efectivo",
        description = "BNK-RF-12 — Gestiona el ciclo de vida de la caja. " +
                      "Transiciones permitidas: ACTIVE ↔ INACTIVE, ACTIVE/INACTIVE → CLOSED (irreversible). " +
                      "Para CLOSED: el saldo debe ser 0 y no puede haber arqueos abiertos. " +
                      "El motivo es obligatorio para INACTIVE y CLOSED (mínimo 10 caracteres). " +
                      "La fecha de cierre es obligatoria solo para CLOSED. " +
                      "Requiere permiso PERM_CHANGE_CASH_STATUS.", 
                      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ChangeCashStatusRequest.class)
            )
        )
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado de caja actualizado exitosamente", content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = CashDTO.class))),
        @ApiResponse(responseCode = "400", description = "Transición no permitida, saldo no cero o arqueos abiertos"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Sin permiso PERM_CHANGE_CASH_STATUS"),
        @ApiResponse(responseCode = "404", description = "Caja no encontrada")
    })
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERM_CHANGE_CASH_STATUS') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody ChangeCashStatusRequest request,
            BindingResult bindingResult) {
        return cashService.changeCashStatus(id, request, bindingResult);
    }

    /**
     * BNK-RF-13 — Consultar cajas (listado paginado con filtros).
     * POST /api/v1/cash/search
     */
    @Operation(
        summary = "Consultar cajas de efectivo",
        description = "BNK-RF-13 — Retorna un listado paginado de cajas con soporte de filtros dinámicos. " +
                      "Permite filtrar por código, nombre, tipo, estado, ubicación, moneda y libro contable. " +
                      "Máximo 100 registros por página. " +
                      "Requiere permiso PERM_VIEW_CASH.",
                      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = false,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DataTableRequest.class)
            )
        )
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado de cajas obtenido exitosamente", content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = CashDTO.class))),
        @ApiResponse(responseCode = "400", description = "Parámetros de búsqueda inválidos"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Sin permiso PERM_VIEW_CASH")
    })
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_VIEW_CASH') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> search(
            @RequestBody(required = false) DataTableRequest request) {
        return cashService.getCashes(request);
    }

    /**
     * BNK-RF-13 — Consultar detalle de una caja.
     * GET /api/v1/cash/{id}
     */
    @Operation(
        summary = "Consultar detalle de una caja de efectivo",
        description = "BNK-RF-13 — Retorna el detalle completo de una caja incluyendo responsables, " +
                      "configuración de límites, información contable y vínculos a movimientos y arqueos. " +
                      "Requiere permiso PERM_VIEW_CASH."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detalle de caja obtenido exitosamente", content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = CashDTO.class))),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Sin permiso PERM_VIEW_CASH"),
        @ApiResponse(responseCode = "404", description = "Caja no encontrada")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VIEW_CASH') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> detail(
            @PathVariable Long id) {
        return cashService.getCashById(id);
    }
}
