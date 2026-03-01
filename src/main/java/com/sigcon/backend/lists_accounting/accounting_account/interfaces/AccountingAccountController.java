package com.sigcon.backend.lists_accounting.accounting_account.interfaces;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import com.sigcon.backend.lists_accounting.accounting_account.application.CreateAccountingAccountRequest;
import com.sigcon.backend.lists_accounting.accounting_account.application.UpdateAccountingAccountRequest;
import com.sigcon.backend.lists_accounting.accounting_account.domain.service.AccountingAccountService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/accounting-accounts")
@RequiredArgsConstructor
public class AccountingAccountController {

    private final AccountingAccountService accountingAccountService;

    /**
     * CFG-RF-06: Consultar cuentas contables existentes
     * POST /api/v1/accounting-accounts
     */
    @Operation(summary = "Consultar cuentas contables", description = "Retorna un listado paginado de cuentas contables activas según los filtros especificados en el requerimiento CFG-RF-06. "
            +
            "Permite búsqueda por código, nombre, clase, nivel y naturaleza de cuenta. " +
            "Requiere permisos PERM_VIEW_ACCOUNTING_ACCOUNT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado obtenido exitosamente"),
            @ApiResponse(responseCode = "200", description = "No existen cuentas con esos criterios de búsqueda"),
            @ApiResponse(responseCode = "400", description = "Error en los parámetros de búsqueda o filtros inválidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado - Token JWT ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "No autorizado - Permiso PERM_VIEW_ACCOUNTING_ACCOUNT requerido")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING_ACCOUNT')")
    public ResponseEntity<?> getAccountingAccounts(
            @RequestBody(required = false) DataTableRequest dtRequest) {
        try {
            // DataTableRequest puede venir null, crear uno por defecto
            if (dtRequest == null) {
                dtRequest = new DataTableRequest();
                dtRequest.setStart(0);
                dtRequest.setLength(10);
                dtRequest.setDraw(1);
            }

            return accountingAccountService.getAccountingAccounts(dtRequest);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson
                    .getErrorRespondMessage(Optional.of("Error al consultar datos, intente nuevamente.")));
        }
    }

    /**
     * CFG-RF-05: Crear cuenta contable
     * POST /api/v1/accounting-accounts/store
     */
    @Operation(summary = "Crear nueva cuenta contable", description = "Registra una nueva cuenta contable en el catálogo PUC según las especificaciones del requerimiento CFG-RF-05. "
            +
            "Valida unicidad de código y nombre, y aplica reglas de naturaleza según la clase de cuenta. " +
            "Requiere permisos PERM_CREATE_ACCOUNTING_ACCOUNT")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta contable creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos: código/nombre obligatorio, longitud excedida, o naturaleza inconsistente con la clase"),
            @ApiResponse(responseCode = "409", description = "Conflicto: ya existe una cuenta con el mismo código o nombre registrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado - Token JWT ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "No autorizado - Permiso PERM_CREATE_ACCOUNTING_ACCOUNT requerido"),
            @ApiResponse(responseCode = "500", description = "Error interno al guardar la cuenta en la base de datos")
    })
    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_ACCOUNTING_ACCOUNT')")
    public ResponseEntity<?> createAccountingAccount(
            @Valid @RequestBody CreateAccountingAccountRequest request,
            BindingResult bindingResult) {
        try {
            // TODO: Obtener userId y companyId del contexto de seguridad (JwtService)
            Long userId = 1L; // Temporal - reemplazar con jwtService.getUserIdFromToken()
            Long companyId = 1L; // Temporal - reemplazar con usuario actual

            return accountingAccountService.createAccountingAccount(request, bindingResult, userId, companyId);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * CFG-RF-07: Editar cuenta contable
     * PUT /api/v1/accounting-accounts/update
     */
    @Operation(summary = "Actualizar cuenta contable existente", description = "Modifica los campos editables de una cuenta contable según las especificaciones del requerimiento CFG-RF-07. "
            +
            "Campos como código, clase y nivel NO son editables para mantener integridad del catálogo. " +
            "Requiere permisos PERM_UPDATE_ACCOUNTING_ACCOUNT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta actualizada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, cuenta inactiva, o intento de modificar campos no editables"),
            @ApiResponse(responseCode = "404", description = "Cuenta contable no encontrada con el ID especificado"),
            @ApiResponse(responseCode = "409", description = "Conflicto: el nuevo nombre ya existe en otra cuenta activa"),
            @ApiResponse(responseCode = "401", description = "No autenticado - Token JWT ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "No autorizado - Permiso PERM_UPDATE_ACCOUNTING_ACCOUNT requerido"),
            @ApiResponse(responseCode = "500", description = "Error interno al guardar los cambios")
    })
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('PERM_UPDATE_ACCOUNTING_ACCOUNT')")
    public ResponseEntity<?> updateAccountingAccount(
            @Valid @RequestBody UpdateAccountingAccountRequest request,
            BindingResult bindingResult) {
        try {
            // TODO: Obtener userId del contexto de seguridad (JwtService)
            Long userId = 1L; // Temporal - reemplazar con jwtService.getUserIdFromToken()

            return accountingAccountService.updateAccountingAccount(request, bindingResult, userId);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * CFG-RF-08: Eliminar (Inactivar) cuenta contable
     * DELETE /api/v1/accounting-accounts/delete/{id}
     */
    @Operation(summary = "Inactivar cuenta contable (eliminación lógica)", description = "Realiza un borrado lógico de una cuenta contable marcándola como inactiva según el requerimiento CFG-RF-08. "
            +
            "Requiere motivo obligatorio para auditoría. No elimina físicamente el registro. " +
            "Requiere permisos PERM_DELETE_ACCOUNTING_ACCOUNT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta inactivada correctamente"),
            @ApiResponse(responseCode = "400", description = "Cuenta no encontrada, ya inactiva, o motivo de eliminación vacío"),
            @ApiResponse(responseCode = "401", description = "No autenticado - Token JWT ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "No autorizado - Permiso PERM_DELETE_ACCOUNTING_ACCOUNT requerido"),
            @ApiResponse(responseCode = "500", description = "Error interno al procesar la inactivación")
    })
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_ACCOUNTING_ACCOUNT')")
    public ResponseEntity<?> deleteAccountingAccount(
            @PathVariable Long id,
            @RequestParam(name = "reason", required = true) String reason) {
        try {
            // TODO: Obtener userId del contexto de seguridad (JwtService)
            Long userId = 1L; // Temporal - reemplazar con jwtService.getUserIdFromToken()

            return accountingAccountService.deleteAccountingAccount(id, reason, userId);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleMalformedJson(HttpMessageNotReadableException ex) {
        String detail = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        return ResponseEntity.badRequest()
                .body(ErrorRespondJson.getErrorRespondMessage(
                        Optional.of(
                                "El cuerpo de la solicitud contiene un valor inválido o mal formateado: " + detail)));
    }
}