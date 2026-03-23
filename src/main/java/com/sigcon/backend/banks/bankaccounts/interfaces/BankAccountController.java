package com.sigcon.backend.banks.bankaccounts.interfaces;

import com.sigcon.backend.banks.bankaccounts.application.*;
import com.sigcon.backend.banks.bankaccounts.domain.service.BankAccountService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/bank-accounts")
@RequiredArgsConstructor
@Tag(name = "Cuentas Bancarias", description = "Endpoints para gestión de cuentas bancarias")
@SecurityRequirement(name = "bearerAuth")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBusinessException(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(ex.getMessage()))
        );
    }

    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_BANK_ACCOUNT') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(summary = "Crear cuenta bancaria", description = "Registra una nueva cuenta bancaria. Requiere bancos, monedas, PUC y empresas configurados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta creada correctamente"),
            @ApiResponse(responseCode = "400", description = "Error de validación o regla de negocio"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    public ResponseEntity<?> store(
            @Valid @RequestBody CreateBankAccountRequest request,
            BindingResult bindingResult) {
        return bankAccountService.create(request, bindingResult);
    }

    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_VIEW_BANK_ACCOUNT') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(summary = "Consultar cuentas bancarias",
            description = "Consulta paginada tipo DataTable. Campos buscables en columns[].data: " +
                    "code, accountNumber, accountNumberMasked, accountName, accountType, " +
                    "bankName, bankId, companyName, companyId, currencyCode, currencyTypeId, " +
                    "chartOfAccountCode, chartOfAccountName, chartOfAccountId, status, " +
                    "branchName, accountExecutive, bankPhone, description. " +
                    "Si search.value tiene texto y columns está vacío, busca en los campos principales.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consulta realizada correctamente"),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest request) {
        return bankAccountService.findAllPaged(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VIEW_BANK_ACCOUNT') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(summary = "Detalle de cuenta bancaria", description = "Retorna el detalle completo. El número de cuenta se devuelve enmascarado (****1234).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle obtenido correctamente"),
            @ApiResponse(responseCode = "400", description = "Cuenta no encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    public ResponseEntity<?> detail(
            @Parameter(description = "ID de la cuenta bancaria") @PathVariable Long id) {
        return bankAccountService.getDetail(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_BANK_ACCOUNT') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(summary = "Actualizar cuenta bancaria", description = "Actualiza campos permitidos. No se pueden modificar código, número, moneda ni cuenta contable si hay movimientos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta actualizada correctamente"),
            @ApiResponse(responseCode = "400", description = "Error de validación"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    public ResponseEntity<?> update(
            @Parameter(description = "ID de la cuenta") @PathVariable Long id,
            @Valid @RequestBody UpdateBankAccountRequest request,
            BindingResult bindingResult) {
        return bankAccountService.update(id, request, bindingResult);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_BANK_ACCOUNT') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(summary = "Eliminar cuenta bancaria", description = "Eliminación lógica. Si existen chequeras asociadas, retorna error sugiriendo desactivar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta eliminada correctamente"),
            @ApiResponse(responseCode = "400", description = "Dependencias existentes o motivo faltante"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    public ResponseEntity<?> delete(
            @Parameter(description = "ID de la cuenta") @PathVariable Long id,
            @Valid @RequestBody BankAccountDeleteRequest request) {
        return bankAccountService.delete(id, request.getMotivo());
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PERM_UPDATE_BANK_ACCOUNT') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(summary = "Desactivar cuenta bancaria", description = "Cambia el estado a INACTIVA cuando no se puede eliminar por dependencias.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta desactivada correctamente"),
            @ApiResponse(responseCode = "400", description = "Error de validación"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    public ResponseEntity<?> deactivate(
            @Parameter(description = "ID de la cuenta") @PathVariable Long id,
            @Valid @RequestBody BankAccountDeleteRequest request) {
        return bankAccountService.deactivate(id, request.getMotivo());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERM_UPDATE_BANK_ACCOUNT') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(summary = "Cambiar estado de cuenta", description = "Transiciones: ACTIVA↔INACTIVA, SUSPENDIDA, CERRADA (irreversible). Motivo obligatorio para INACTIVA/SUSPENDIDA/CERRADA.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado correctamente"),
            @ApiResponse(responseCode = "400", description = "Error de validación"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    public ResponseEntity<?> changeStatus(
            @Parameter(description = "ID de la cuenta") @PathVariable Long id,
            @Valid @RequestBody BankAccountChangeStatusRequest request,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        return bankAccountService.changeStatus(
                id,
                request.getStatus(),
                request.getMotivo(),
                request.getClosingDate()
        );
    }
}
