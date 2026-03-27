package com.sigcon.backend.banks.checkbooks.domain.service;

import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.model.enums.BankAccountStatus;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.checkbooks.application.*;
import com.sigcon.backend.banks.checkbooks.domain.model.Checkbook;
import com.sigcon.backend.banks.checkbooks.domain.model.enums.CheckbookStatus;
import com.sigcon.backend.banks.checkbooks.domain.repository.CheckbookRepository;
import com.sigcon.backend.banks.checks.domain.repository.CheckRepository;
import com.sigcon.backend.utils.ErrorRespondJson;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CheckbookService {

    private final CheckbookRepository repository;
    private final BankAccountRepository bankAccountRepository;
    private final CheckRepository checkRepository;

    // =========================
    // CREATE / UPDATE
    // =========================
    public Object save(CheckbookRequest request, Long id) {

        boolean isUpdate = id != null;

        BankAccount bankAccount = bankAccountRepository
                .findByIdAndDeletedAtIsNull(request.getBankAccountId())
                .orElse(null);

        if (bankAccount == null)
            return ErrorRespondJson.getErrorRespondMessage(Optional.of("Cuenta no encontrada"));

        if (!bankAccount.getStatus().equals(BankAccountStatus.ACTIVA))
            return ErrorRespondJson.getErrorRespondMessage(Optional.of("Cuenta inactiva"));

        if (!bankAccount.getHandlesCheckbook())
            return ErrorRespondJson.getErrorRespondMessage(Optional.of("Cuenta no permite chequeras"));

        Checkbook entity = isUpdate ? repository.findById(id).orElse(null) : new Checkbook();

        if (isUpdate && entity == null)
            return ErrorRespondJson.getErrorRespondMessage(Optional.of("Chequera no encontrada"));

        if (request.getCheckEndNumber() <= request.getCheckStartNumber())
            return ErrorRespondJson.getErrorRespondMessage(Optional.of("Rango inválido"));

        if (!isUpdate &&
                repository.existsByBankAccount_IdAndCheckbookNumber(
                        request.getBankAccountId(),
                        request.getCheckbookNumber()))
            return ErrorRespondJson.getErrorRespondMessage(Optional.of("Chequera duplicada"));

        List<Checkbook> list = repository.findByBankAccount_Id(request.getBankAccountId());

        for (Checkbook cb : list) {
            if (isUpdate && cb.getId().equals(entity.getId())) continue;

            boolean overlap =
                    request.getCheckStartNumber() <= cb.getCheckEndNumber() &&
                    request.getCheckEndNumber() >= cb.getCheckStartNumber();

            if (overlap)
                return ErrorRespondJson.getErrorRespondMessage(Optional.of("Rango superpuesto"));
        }

        entity.setBankAccount(bankAccount);
        entity.setCompanyId(bankAccount.getCompany().getId());

        entity.setCheckbookNumber(request.getCheckbookNumber());
        entity.setIssuingBank(request.getIssuingBank());
        entity.setCheckStartNumber(request.getCheckStartNumber());
        entity.setCheckEndNumber(request.getCheckEndNumber());
        entity.setReceivedDate(request.getReceivedDate());
        entity.setActivationDate(request.getActivationDate());
        entity.setObservations(request.getObservations());

        int total = (int) (request.getCheckEndNumber() - request.getCheckStartNumber() + 1);
        entity.setTotalChecks(total);

        int used = isUpdate
                ? (int) checkRepository.countByCheckbook_Id(entity.getId())
                : 0;

        entity.setUsedChecks(used);
        entity.setAvailableChecks(total - used);

        entity.setStatus(entity.getAvailableChecks() == 0
                ? CheckbookStatus.AGOTADA
                : request.getStatus());

        Checkbook saved = repository.save(entity);

        return toDto(saved); // ✅ CLAVE
    }

    // =========================
    // DELETE / INACTIVATE
    // =========================
    public Object delete(CheckbookDeleteRequest request) {

        Checkbook entity = repository.findById(request.getId()).orElse(null);

        if (entity == null)
            return ErrorRespondJson.getErrorRespondMessage(Optional.of("Chequera no encontrada"));

        if (request.getReason() == null || request.getReason().length() < 10)
            return ErrorRespondJson.getErrorRespondMessage(Optional.of("Motivo inválido"));

        int used = (int) checkRepository.countByCheckbook_Id(entity.getId());

        entity.setStatus(used == 0
                ? CheckbookStatus.ANULADA
                : CheckbookStatus.BLOQUEADA);

        entity.setObservations(request.getReason());

        Checkbook saved = repository.save(entity);

        return toDto(saved);
    }

    // =========================
    // SEARCH
    // =========================
    public Object search(CheckbookQueryRequest request) {

        List<Checkbook> list = repository.findAll();

        List<CheckbookDTO> result = list.stream()
                .filter(cb -> request.getCheckbookNumber() == null || cb.getCheckbookNumber().contains(request.getCheckbookNumber()))
                .filter(cb -> request.getIssuingBank() == null || cb.getIssuingBank().contains(request.getIssuingBank()))
                .filter(cb -> request.getStatus() == null || cb.getStatus().name().equals(request.getStatus()))
                .filter(cb -> request.getBankAccountId() == null || cb.getBankAccount().getId().equals(request.getBankAccountId()))
                .map(this::toDto)
                .collect(Collectors.toList());

        if (result.isEmpty())
            return ErrorRespondJson.getErrorRespondMessage(Optional.of("Sin resultados"));

        return result;
    }

    // =========================
    // MAPPER
    // =========================
    private CheckbookDTO toDto(Checkbook entity) {
        if (entity == null) return null;

        return CheckbookDTO.builder()
                .id(entity.getId())
                .bankAccountId(entity.getBankAccount().getId())
                .checkbookNumber(entity.getCheckbookNumber())
                .issuingBank(entity.getIssuingBank())
                .checkStartNumber(entity.getCheckStartNumber())
                .checkEndNumber(entity.getCheckEndNumber())
                .totalChecks(entity.getTotalChecks())
                .usedChecks(entity.getUsedChecks())
                .availableChecks(entity.getAvailableChecks())
                .receivedDate(entity.getReceivedDate())
                .activationDate(entity.getActivationDate())
                .status(entity.getStatus())
                .observations(entity.getObservations())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}