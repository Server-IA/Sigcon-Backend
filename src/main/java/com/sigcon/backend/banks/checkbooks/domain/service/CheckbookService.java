package com.sigcon.backend.banks.checkbooks.domain.service;

import com.sigcon.backend.banks.bankaccounts.application.BankAccountDTO;
import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.model.enums.BankAccountStatus;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.banks.application.BankDTO;
import com.sigcon.backend.banks.checkbooks.application.*;
import com.sigcon.backend.banks.checkbooks.domain.model.Checkbook;
import com.sigcon.backend.banks.checkbooks.domain.model.enums.CheckbookStatus;
import com.sigcon.backend.banks.checkbooks.domain.repository.CheckbookRepository;
import com.sigcon.backend.banks.checks.domain.repository.CheckRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.utils.*;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CheckbookService {

    private static final int MAX_PAGE_SIZE = 200;

    private final CheckbookRepository repository;
    private final BankAccountRepository bankAccountRepository;
    private final CheckRepository checkRepository;


    private final DataTableSpecificationBuilder<Checkbook> dataTableSpecificationBuilder = new DataTableSpecificationBuilder<>();

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

        //  VALIDACIÓN ESTADO INICIAL
        if (!isUpdate) {
            if (request.getStatus() == CheckbookStatus.AGOTADA ||
                request.getStatus() == CheckbookStatus.ANULADA) {

                return ErrorRespondJson.getErrorRespondMessage(
                        Optional.of("Estado inicial no permitido - no se puede crear en estado AGOTADA/ANULADA")
                );
            }
        }

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

        // User user = userUtil.getUser();

        entity.setBankAccount(bankAccount);

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

        return toDto(saved);
    }

    // =========================
    // DELETE
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
    public ResponseEntity<?> search(DataTableRequest request) {

        DataTableRequest safeRequest = normalizeDataTableRequest(request);

        int start = Math.max(0, safeRequest.getStart());
        int length = safeRequest.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, MAX_PAGE_SIZE);
        int page = start / safeLength;

        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

        Specification<Checkbook> specification = dataTableSpecificationBuilder.build(safeRequest)
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

        Page<Checkbook> result = repository.findAll(specification, pageable);

        // FILTROS MANUALES DE FECHA
        List<Checkbook> filtered = result.getContent().stream()

                .filter(cb -> safeRequest.getReceivedDateFrom() == null ||
                        !cb.getReceivedDate().isBefore(safeRequest.getReceivedDateFrom()))

                .filter(cb -> safeRequest.getReceivedDateTo() == null ||
                        !cb.getReceivedDate().isAfter(safeRequest.getReceivedDateTo()))

                .filter(cb -> safeRequest.getActivationDateFrom() == null ||
                        (cb.getActivationDate() != null &&
                                !cb.getActivationDate().isBefore(safeRequest.getActivationDateFrom())))

                .filter(cb -> safeRequest.getActivationDateTo() == null ||
                        (cb.getActivationDate() != null &&
                                !cb.getActivationDate().isAfter(safeRequest.getActivationDateTo())))

                .toList();

        return ResponseEntity.ok(
                DataTableResponse.from(
                        filtered.stream().map(this::toDto).toList(),
                        safeRequest.getDraw()
                )
        );
    }

    // =========================
    // NORMALIZER (CLAVE)
    // =========================
    private DataTableRequest normalizeDataTableRequest(DataTableRequest request) {
        DataTableRequest safe = request != null ? request : new DataTableRequest();

        if (safe.getLength() == 0) {
            safe.setLength(20);
        }
        if (safe.getColumns() == null) {
            safe.setColumns(new ArrayList<>());
        }
        if (safe.getSearch() == null) {
            safe.setSearch(new DataTableRequest.DataTableSearch("", false));
        }
        return safe;
    }

    // =========================
    // MAPPER
    // =========================
    private CheckbookDTO toDto(Checkbook entity) {
        if (entity == null) return null;

        return CheckbookDTO.builder()
                .id(entity.getId())
                .bankAccount(BankAccountDTO.builder()
                        .id(entity.getBankAccount().getId())
                        .accountName(entity.getBankAccount().getAccountName())
                        .accountType(entity.getBankAccount().getAccountType())
                        .bankDTO(BankDTO.builder()
                                .id(entity.getBankAccount().getBank().getId())
                                .name(entity.getBankAccount().getBank().getName())
                                .build())
                        .build())
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