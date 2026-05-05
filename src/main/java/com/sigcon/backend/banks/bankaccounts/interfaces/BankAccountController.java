package com.sigcon.backend.banks.bankaccounts.interfaces;

import com.sigcon.backend.banks.bankaccounts.application.*;
import com.sigcon.backend.banks.bankaccounts.domain.service.BankAccountService;
import com.sigcon.backend.banks.financialmovements.application.CreateBankFinancialMovementRequest;
import com.sigcon.backend.banks.financialmovements.application.MatchVoucherRequest;
import com.sigcon.backend.banks.financialmovements.application.UpdateLastReconciliationRequest;
import com.sigcon.backend.banks.financialmovements.domain.service.FinancialMovementService;
import com.sigcon.backend.banks.reconciliation.application.CreateBankReconciliationSessionRequest;
import com.sigcon.backend.banks.reconciliation.application.UpdateBankReconciliationStatementBalancesRequest;
import com.sigcon.backend.banks.reconciliation.domain.service.BankReconciliationSessionService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/bank-accounts")
@RequiredArgsConstructor
@Tag(name = "5. Módulo de Bancos - Cuentas Bancarias", description = "Endpoints para gestión de cuentas bancarias")
@SecurityRequirement(name = "bearerAuth")
public class BankAccountController {

    private final BankAccountService bankAccountService;
    private final FinancialMovementService financialMovementService;
    private final BankReconciliationSessionService bankReconciliationSessionService;

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBusinessException(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(ex.getMessage()))
        );
    }

    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.CREAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
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
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
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
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
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
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
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
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.ELIMINAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
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
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
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
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
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

    @GetMapping("/{id}/financial-movements")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Listar movimientos financieros de la cuenta",
            description = "Movimientos registrados para conciliación. Use unmatchedOnly=true para pendientes de emparejar con cheques.")
    public ResponseEntity<?> listFinancialMovements(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean unmatchedOnly) {
        return financialMovementService.listForBankAccount(id, unmatchedOnly);
    }

    @PostMapping("/{id}/financial-movements")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Registrar movimiento bancario manual",
            description = "Alta de línea de extracto u operación bancaria para conciliación (importe negativo = egreso).")
    public ResponseEntity<?> createFinancialMovement(
            @PathVariable Long id,
            @Valid @RequestBody CreateBankFinancialMovementRequest request,
            BindingResult bindingResult) {
        return financialMovementService.createForBankAccount(id, request, bindingResult);
    }

    @PutMapping("/{id}/last-reconciliation")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Registrar fecha de última conciliación",
            description = "Cierra el período de extracto hasta la fecha indicada.")
    public ResponseEntity<?> updateLastReconciliation(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLastReconciliationRequest request,
            BindingResult bindingResult) {
        return bankAccountService.updateLastReconciliationDate(id, request, bindingResult);
    }

    @GetMapping("/{id}/reconciliation-sessions")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Listar sesiones de conciliación de la cuenta")
    public ResponseEntity<?> listReconciliationSessions(@PathVariable Long id) {
        return bankReconciliationSessionService.listByBankAccount(id);
    }

    @PostMapping("/{id}/reconciliation-sessions")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Crear sesión de conciliación (borrador)")
    public ResponseEntity<?> createReconciliationSession(
            @PathVariable Long id,
            @Valid @RequestBody CreateBankReconciliationSessionRequest request,
            BindingResult bindingResult) {
        return bankReconciliationSessionService.create(id, request, bindingResult);
    }

    @PutMapping("/{id}/reconciliation-sessions/{sessionId}/close")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Cerrar sesión de conciliación", description = "Marca la sesión como cerrada y actualiza la fecha de última conciliación de la cuenta.")
    public ResponseEntity<?> closeReconciliationSession(
            @PathVariable Long id,
            @PathVariable Long sessionId) {
        return bankReconciliationSessionService.close(id, sessionId);
    }

    @GetMapping("/{id}/reconciliation-sessions/{sessionId}/summary")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Resumen numérico de conciliación",
            description = "Cuadre: aritmética del extracto (saldo inicial + movimientos del periodo vs saldo final) y comparación con saldo libro aproximado (saldo inicial cuenta + comprobantes hasta la fecha fin).")
    public ResponseEntity<?> getReconciliationSummary(
            @PathVariable Long id,
            @PathVariable Long sessionId) {
        return bankReconciliationSessionService.getSummary(id, sessionId);
    }

    @PutMapping("/{id}/reconciliation-sessions/{sessionId}/statement-balances")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Actualizar saldos del extracto en borrador",
            description = "Solo sesiones en estado borrador. Envíe los campos que desee cambiar.")
    public ResponseEntity<?> updateReconciliationStatementBalances(
            @PathVariable Long id,
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateBankReconciliationStatementBalancesRequest request,
            BindingResult bindingResult) {
        return bankReconciliationSessionService.updateStatementBalances(id, sessionId, request, bindingResult);
    }

    @PostMapping(value = "/{id}/financial-movements/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Importar movimientos desde CSV",
            description = "Formato por línea: fecha;importe;descripcion;referencia (punto o coma decimal). Primera línea puede ser encabezado.")
    public ResponseEntity<?> importFinancialMovementsCsv(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long reconciliationSessionId) {
        return financialMovementService.importCsv(id, reconciliationSessionId, file);
    }

    @GetMapping("/{id}/financial-movements/{movementId}/voucher-suggestions")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Sugerencias de comprobantes para emparejar un movimiento")
    public ResponseEntity<?> voucherSuggestions(
            @PathVariable Long id,
            @PathVariable Long movementId) {
        return financialMovementService.suggestVouchers(id, movementId);
    }

    @PutMapping("/{id}/financial-movements/{movementId}/match-voucher")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Emparejar movimiento con comprobante (voucher)")
    public ResponseEntity<?> matchMovementToVoucher(
            @PathVariable Long id,
            @PathVariable Long movementId,
            @Valid @RequestBody MatchVoucherRequest request,
            BindingResult bindingResult) {
        return financialMovementService.matchVoucher(id, movementId, request, bindingResult);
    }

    /**
     * QA-BLOQUE-AP (2026-04-29): sugerencias contra JournalEntries (asientos
     * contables) para empresas que no usan Vouchers legacy.
     */
    @GetMapping("/{id}/financial-movements/{movementId}/journal-entry-suggestions")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Sugerencias de asientos contables (JE) para emparejar movimiento")
    public ResponseEntity<?> journalEntrySuggestions(
            @PathVariable Long id,
            @PathVariable Long movementId) {
        return financialMovementService.suggestJournalEntries(id, movementId);
    }

    @PutMapping("/{id}/financial-movements/{movementId}/match-journal-entry")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Emparejar movimiento con asiento contable (JournalEntry)")
    public ResponseEntity<?> matchMovementToJournalEntry(
            @PathVariable Long id,
            @PathVariable Long movementId,
            @RequestBody MatchVoucherRequest request) {
        return financialMovementService.matchJournalEntry(id, movementId, request);
    }

    @PutMapping("/{id}/financial-movements/{movementId}/unmatch")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Quitar emparejamiento con comprobante")
    public ResponseEntity<?> unmatchMovement(
            @PathVariable Long id,
            @PathVariable Long movementId) {
        return financialMovementService.unmatch(id, movementId);
    }
}
