package com.sigcon.backend.third_parties.bank_accounts.interfaces.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.third_parties.bank_accounts.application.LinkBankAccountRequest;
import com.sigcon.backend.third_parties.bank_accounts.domain.service.ThirdPartyBankAccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * TER-05: Controller REST para la gestion de cuentas bancarias vinculadas a terceros.
 */
@RestController
@RequestMapping("/api/v1/third-parties/{thirdPartyId}/bank-accounts")
@RequiredArgsConstructor
@Tag(name = "3. Modulo de Terceros - Cuentas Bancarias",
        description = "Endpoints para vincular y desvincular cuentas bancarias a terceros")
public class ThirdPartyBankAccountController {

    private final ThirdPartyBankAccountService thirdPartyBankAccountService;

    /**
     * GET /api/v1/third-parties/{thirdPartyId}/bank-accounts
     */
    @Operation(summary = "Listar cuentas bancarias de un tercero",
            description = "Retorna todas las cuentas bancarias vinculadas activamente a un tercero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuentas bancarias obtenidas exitosamente"),
            @ApiResponse(responseCode = "400", description = "TPBA_001: El tercero no existe")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_THIRD_PARTIES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getByThirdParty(@PathVariable Long thirdPartyId) {
        return thirdPartyBankAccountService.getByThirdParty(thirdPartyId);
    }

    /**
     * POST /api/v1/third-parties/{thirdPartyId}/bank-accounts
     */
    @Operation(summary = "Vincular cuenta bancaria a un tercero",
            description = "Asocia una cuenta bancaria del sistema a un tercero. "
                    + "Si ya existe la vinculacion, retorna HTTP 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta bancaria vinculada exitosamente"),
            @ApiResponse(responseCode = "400", description = "TPBA_001/TPBA_002: Tercero o cuenta no existe"),
            @ApiResponse(responseCode = "409", description = "TPBA_003: Ya esta vinculada")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('PERM_CREATE_THIRD_PARTIES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> linkBankAccount(
            @PathVariable Long thirdPartyId,
            @Valid @RequestBody LinkBankAccountRequest request) {
        return thirdPartyBankAccountService.linkBankAccount(thirdPartyId, request);
    }

    /**
     * DELETE /api/v1/third-parties/{thirdPartyId}/bank-accounts/{linkId}
     */
    @Operation(summary = "Desvincular cuenta bancaria de un tercero",
            description = "Realiza un soft delete de la vinculacion entre cuenta bancaria y tercero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta bancaria desvinculada exitosamente"),
            @ApiResponse(responseCode = "400", description = "TPBA_004: Vinculacion no existe")
    })
    @DeleteMapping("/{linkId}")
    @PreAuthorize("hasAuthority('PERM_DELETE_THIRD_PARTIES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> unlinkBankAccount(
            @PathVariable Long thirdPartyId,
            @PathVariable Long linkId) {
        return thirdPartyBankAccountService.unlinkBankAccount(linkId);
    }

    /**
     * PATCH /api/v1/third-parties/{thirdPartyId}/bank-accounts/{linkId}/primary
     * PT-02 (TER-RF-05): marca una cuenta vinculada como principal del tercero
     * sin desvincularla. Desmarca automaticamente la principal anterior.
     */
    @Operation(summary = "Marcar cuenta bancaria como principal",
            description = "PT-02 (TER-RF-05): establece una cuenta vinculada como principal del tercero. "
                    + "Desmarca automaticamente la cuenta principal anterior (solo una principal por tercero).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta marcada como principal"),
            @ApiResponse(responseCode = "400", description = "TPBA_004: Vinculacion no existe")
    })
    @PatchMapping("/{linkId}/primary")
    @PreAuthorize("hasAuthority('PERM_UPDATE_THIRD_PARTIES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> setAsPrimary(
            @PathVariable Long thirdPartyId,
            @PathVariable Long linkId) {
        return thirdPartyBankAccountService.setAsPrimary(linkId);
    }
}
