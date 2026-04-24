package com.sigcon.backend.third_parties.bank_accounts.domain.service;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.third_parties.bank_accounts.application.LinkBankAccountRequest;
import com.sigcon.backend.third_parties.bank_accounts.application.ThirdPartyBankAccountDTO;
import com.sigcon.backend.third_parties.bank_accounts.domain.model.ThirdPartyBankAccount;
import com.sigcon.backend.third_parties.bank_accounts.domain.repository.ThirdPartyBankAccountRepository;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * TER-05: Servicio para gestionar las vinculaciones de cuentas bancarias a terceros.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ThirdPartyBankAccountService {

    private final ThirdPartyBankAccountRepository thirdPartyBankAccountRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final BankAccountRepository bankAccountRepository;
    private final AuditPublisher auditPublisher;

    /**
     * Lista todas las cuentas bancarias vinculadas a un tercero.
     *
     * @param thirdPartyId ID del tercero
     * @return lista de vinculaciones como DTOs
     */
    public ResponseEntity<?> getByThirdParty(Long thirdPartyId) {
        // Validar que el tercero exista
        thirdPartyRepository.findById(thirdPartyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TPBA_001: El tercero no existe."));

        List<ThirdPartyBankAccountDTO> accounts = thirdPartyBankAccountRepository
                .findByThirdPartyIdAndDeletedAtIsNull(thirdPartyId)
                .stream()
                .map(this::toDTO)
                .toList();

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Cuentas bancarias del tercero obtenidas exitosamente"),
                        Optional.of(accounts)));
    }

    /**
     * Vincula una cuenta bancaria a un tercero.
     *
     * @param thirdPartyId ID del tercero
     * @param request      datos de la vinculacion
     * @return respuesta con la vinculacion creada
     */
    public ResponseEntity<?> linkBankAccount(Long thirdPartyId, LinkBankAccountRequest request) {
        // 1. Validar que el tercero exista
        ThirdParty thirdParty = thirdPartyRepository.findById(thirdPartyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TPBA_001: El tercero no existe."));

        // 2. Validar que la cuenta bancaria exista
        BankAccount bankAccount = bankAccountRepository.findByIdAndDeletedAtIsNull(request.getBankAccountId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "TPBA_002: La cuenta bancaria no existe."));

        // 3. Validar que no exista ya la vinculacion
        if (thirdPartyBankAccountRepository.existsByThirdPartyIdAndBankAccountIdAndDeletedAtIsNull(
                thirdPartyId, request.getBankAccountId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("TPBA_003: La cuenta bancaria ya esta vinculada a este tercero.")));
        }

        // 4. Crear la vinculacion
        ThirdPartyBankAccount link = ThirdPartyBankAccount.builder()
                .thirdParty(thirdParty)
                .bankAccount(bankAccount)
                .isPrimary(request.getIsPrimary() != null ? request.getIsPrimary() : false)
                .build();

        ThirdPartyBankAccount saved = thirdPartyBankAccountRepository.save(link);
        auditPublisher.publishCreate(AuditModule.TER, "ThirdPartyBankAccount", link.getId(), "ThirdPartyBankAccount creado id=" + link.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Cuenta bancaria vinculada exitosamente al tercero"),
                        Optional.of(toDTO(saved))));
    }

    /**
     * Desvincula (soft delete) una cuenta bancaria de un tercero.
     *
     * @param linkId ID de la vinculacion
     * @return respuesta de exito
     */
    public ResponseEntity<?> unlinkBankAccount(Long linkId) {
        ThirdPartyBankAccount link = thirdPartyBankAccountRepository.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TPBA_004: La vinculacion de cuenta bancaria no existe."));

        thirdPartyBankAccountRepository.delete(link);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Cuenta bancaria desvinculada exitosamente del tercero"),
                        Optional.empty()));
    }

    // =========================================================================
    // METODOS PRIVADOS
    // =========================================================================

    private ThirdPartyBankAccountDTO toDTO(ThirdPartyBankAccount entity) {
        return ThirdPartyBankAccountDTO.builder()
                .id(entity.getId())
                .thirdPartyId(entity.getThirdParty().getId())
                .bankAccountId(entity.getBankAccount().getId())
                .bankAccountCode(entity.getBankAccount().getCode())
                .bankName(entity.getBankAccount().getBank() != null
                        ? entity.getBankAccount().getBank().getName() : null)
                .accountNumber(entity.getBankAccount().getAccountNumber())
                .isPrimary(entity.getIsPrimary())
                .build();
    }
}
