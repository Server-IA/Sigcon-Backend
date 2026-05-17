package com.sigcon.backend.banks.financialmovements.interfaces.controller;

import com.sigcon.backend.banks.financialmovements.application.CreateBankFinancialMovementRequest;
import com.sigcon.backend.banks.financialmovements.domain.service.FinancialMovementService;
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
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para gestion de movimientos financieros.
 * Permite listar, crear y consultar movimientos bancarios manuales.
 */
@RestController
@RequestMapping("/api/v1/financial-movements")
@RequiredArgsConstructor
@Tag(name = "5. Modulo de Bancos y Cajas - Movimientos Financieros",
        description = "Endpoints para gestion de movimientos financieros bancarios")
@SecurityRequirement(name = "bearerAuth")
public class FinancialMovementController {

    private final FinancialMovementService financialMovementService;

    /**
     * Listar movimientos de una cuenta bancaria.
     *
     * @param bankAccountId ID de la cuenta bancaria
     * @param unmatchedOnly si es true, solo devuelve movimientos no conciliados
     * @return lista de movimientos financieros
     */
    /**
     * Buscar todos los movimientos del tenant paginados (DataTable).
     * Sin requerir bankAccountId — devuelve todos los movimientos de la empresa actual.
     */
    @PostMapping("")
    @Operation(summary = "Buscar movimientos financieros (DataTable)",
               description = "Devuelve movimientos financieros de TODAS las cuentas del tenant, paginados")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista paginada"),
        @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> search(@RequestBody DataTableRequest request) {
        return financialMovementService.search(request);
    }

    @GetMapping("")
    @Operation(summary = "Listar movimientos financieros", description = "Obtiene los movimientos financieros de una cuenta bancaria, con opcion de filtrar solo no conciliados")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de movimientos obtenida exitosamente"),
        @ApiResponse(responseCode = "400", description = "ID de cuenta bancaria invalido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Cuenta bancaria no encontrada")
    })
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_FINANCIAL_MOVEMENT','TEMP_PERM_VIEW_FINANCIAL_MOVEMENT','TEMP_VIEW_FINANCIAL_MOVEMENT','PERM_BNK.MOVIMIENTOS.VER','TEMP_PERM_BNK.MOVIMIENTOS.VER','TEMP_BNK.MOVIMIENTOS.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> list(
            @RequestParam Long bankAccountId,
            @RequestParam(required = false, defaultValue = "false") boolean unmatchedOnly) {
        return financialMovementService.listForBankAccount(bankAccountId, unmatchedOnly);
    }

    /**
     * Registrar un nuevo movimiento financiero manual.
     *
     * @param bankAccountId ID de la cuenta bancaria
     * @param request datos del movimiento
     * @param bindingResult resultado de validacion
     * @return movimiento creado
     */
    @PostMapping("/store")
    @Operation(summary = "Crear movimiento financiero manual", description = "Registra un nuevo movimiento financiero manual para una cuenta bancaria")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Movimiento financiero creado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos invalidos o error de validacion"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Cuenta bancaria no encontrada")
    })
    @PreAuthorize("hasAnyAuthority('PERM_CREATE_FINANCIAL_MOVEMENT','TEMP_PERM_CREATE_FINANCIAL_MOVEMENT','TEMP_CREATE_FINANCIAL_MOVEMENT','PERM_BNK.MOVIMIENTOS.CREAR','TEMP_PERM_BNK.MOVIMIENTOS.CREAR','TEMP_BNK.MOVIMIENTOS.CREAR','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> store(
            @RequestParam Long bankAccountId,
            @Valid @RequestBody CreateBankFinancialMovementRequest request,
            BindingResult bindingResult) {
        return financialMovementService.createForBankAccount(bankAccountId, request, bindingResult);
    }

    /**
     * Obtener detalle de un movimiento financiero por ID.
     *
     * @param id ID del movimiento
     * @return detalle del movimiento financiero
     */
    @GetMapping("/{id}")
    @Operation(summary = "Obtener movimiento financiero por ID", description = "Consulta el detalle de un movimiento financiero especifico")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detalle del movimiento obtenido exitosamente"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Movimiento financiero no encontrado")
    })
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_FINANCIAL_MOVEMENT','TEMP_PERM_VIEW_FINANCIAL_MOVEMENT','TEMP_VIEW_FINANCIAL_MOVEMENT','PERM_BNK.MOVIMIENTOS.VER','TEMP_PERM_BNK.MOVIMIENTOS.VER','TEMP_BNK.MOVIMIENTOS.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return financialMovementService.listForBankAccount(id, false);
    }

    /**
     * QA HU-026: matching automatico de movimientos sin cruzar contra comprobantes
     * por similitud (mismo monto, fecha +/- N dias). Reusa la infraestructura
     * existente de matchVoucher/suggestVouchers (HU-052/053 ya tiene endpoints
     * en BankAccountController bajo /bank-accounts/{id}/financial-movements/...).
     */
    @PostMapping("/auto-match")
    @Operation(summary = "Matching automatico de movimientos no cruzados",
               description = "HU-026: el sistema busca asientos contables (POSTED) cuyo monto y "
                           + "fecha (+/- N dias) coincidan con movimientos no cruzados. "
                           + "Marca matchedVoucherId automaticamente. Reporta cuantos quedaron sin emparejar.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Matching ejecutado")
    })
    @PreAuthorize("hasAnyAuthority('PERM_AUTO_MATCH_FINANCIAL_MOVEMENT','TEMP_PERM_AUTO_MATCH_FINANCIAL_MOVEMENT','TEMP_AUTO_MATCH_FINANCIAL_MOVEMENT','PERM_BNK.MOVIMIENTOS.CONCILIAR','TEMP_PERM_BNK.MOVIMIENTOS.CONCILIAR','TEMP_BNK.MOVIMIENTOS.CONCILIAR','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> autoMatch(
            @RequestParam(required = false) Long bankAccountId,
            @RequestParam(required = false, defaultValue = "3") Integer dateToleranceDays) {
        return financialMovementService.autoMatchMovements(bankAccountId, dateToleranceDays);
    }
}
