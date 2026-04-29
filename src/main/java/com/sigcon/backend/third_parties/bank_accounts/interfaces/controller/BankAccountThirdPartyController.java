package com.sigcon.backend.third_parties.bank_accounts.interfaces.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * HU-TER-05 (2026-04-27): Controller inverso. Permite consultar y vincular
 * terceros a una cuenta bancaria desde el modulo BNK. La logica es la misma
 * que en {@link ThirdPartyBankAccountController} (un solo `ThirdPartyBankAccount`
 * por par tercero/cuenta), pero exposicion bidireccional para que el contador
 * pueda asignar terceros desde donde le sea mas natural.
 */
@RestController
@RequestMapping("/api/v1/bank-accounts/{bankAccountId}/third-parties")
@RequiredArgsConstructor
@Tag(name = "5. Modulo de Bancos y Cajas - Terceros vinculados",
        description = "Endpoints inversos para consultar y vincular terceros a una cuenta bancaria. "
                + "Reflejan la misma tabla que TER-05; ambos lados quedan sincronizados.")
public class BankAccountThirdPartyController {

    private final ThirdPartyBankAccountService thirdPartyBankAccountService;

    @Operation(summary = "Listar terceros vinculados a una cuenta bancaria",
            description = "Lookup inverso del endpoint /api/v1/third-parties/{id}/bank-accounts. "
                    + "Util para mostrar en la ficha de la cuenta bancaria que terceros la tienen asociada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Terceros vinculados obtenidos exitosamente"),
            @ApiResponse(responseCode = "400", description = "TPBA_002: La cuenta bancaria no existe")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_THIRD_PARTIES') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getByBankAccount(@PathVariable Long bankAccountId) {
        return thirdPartyBankAccountService.getByBankAccount(bankAccountId);
    }

    @Operation(summary = "Vincular tercero a esta cuenta bancaria",
            description = "Recibe el ID del tercero y crea la vinculacion. Equivalente a llamar "
                    + "POST /api/v1/third-parties/{thirdPartyId}/bank-accounts con el bankAccountId, "
                    + "pero invertido para UX desde la pantalla BNK.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tercero vinculado exitosamente"),
            @ApiResponse(responseCode = "400", description = "TPBA_001/TPBA_002 tercero o cuenta no existe"),
            @ApiResponse(responseCode = "409", description = "TPBA_003: vinculacion ya existe")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('PERM_CREATE_THIRD_PARTIES') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> linkThirdParty(
            @PathVariable Long bankAccountId,
            @Valid @RequestBody LinkThirdPartyRequest request) {
        LinkBankAccountRequest linkRequest = LinkBankAccountRequest.builder()
                .bankAccountId(bankAccountId)
                .isPrimary(request.getIsPrimary())
                .build();
        return thirdPartyBankAccountService.linkBankAccount(request.getThirdPartyId(), linkRequest);
    }

    /**
     * Request del lado BNK: el frontend envia el ID del tercero a vincular
     * (la cuenta bancaria viene del path).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkThirdPartyRequest {
        @NotNull(message = "El ID del tercero es obligatorio")
        private Long thirdPartyId;
        private Boolean isPrimary;
    }
}
