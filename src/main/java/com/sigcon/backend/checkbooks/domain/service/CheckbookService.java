package com.sigcon.backend.checkbooks.domain.service;

import com.sigcon.backend.checkbooks.application.*;
import com.sigcon.backend.checkbooks.domain.model.Checkbook;
import com.sigcon.backend.checkbooks.domain.model.enums.CheckbookStatus;
import com.sigcon.backend.checkbooks.domain.repository.CheckbookRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckbookService {

    private final CheckbookRepository repository;

    public Object save(CheckbookRequest request, Long id) {

        boolean isUpdate = id != null;

        Checkbook entity = isUpdate
                ? repository.findById(id).orElse(null)
                : new Checkbook();

        if (isUpdate && entity == null) {
            throw new RuntimeException("BNK-ERR-073: Chequera no encontrada");
        }

        if (request.getCheckEndNumber() <= request.getCheckStartNumber()) {
            throw new RuntimeException("BNK-ERR-061: Rango inválido");
        }

        if (!isUpdate &&
                repository.existsByBankAccountIdAndCheckbookNumber(
                        request.getBankAccountId(), request.getCheckbookNumber())) {

            throw new RuntimeException("BNK-ERR-060: Duplicidad");
        }

        List<Checkbook> list = repository.findByBankAccountId(request.getBankAccountId());

        for (Checkbook cb : list) {
            if (isUpdate && cb.getId().equals(entity.getId())) continue;

            boolean overlap =
                    request.getCheckStartNumber() <= cb.getCheckEndNumber() &&
                    request.getCheckEndNumber() >= cb.getCheckStartNumber();

            if (overlap) {
                throw new RuntimeException("BNK-ERR-065: Rango superpuesto");
            }
        }

        entity.setBankAccountId(request.getBankAccountId());
        entity.setCheckbookNumber(request.getCheckbookNumber());
        entity.setIssuingBank(request.getIssuingBank());
        entity.setCheckStartNumber(request.getCheckStartNumber());
        entity.setCheckEndNumber(request.getCheckEndNumber());
        entity.setReceivedDate(request.getReceivedDate());
        entity.setActivationDate(request.getActivationDate());
        entity.setObservations(request.getObservations());

        int total = (int) (request.getCheckEndNumber() - request.getCheckStartNumber() + 1);
        entity.setTotalChecks(total);

        if (!isUpdate) entity.setUsedChecks(0);

        int available = total - entity.getUsedChecks();
        entity.setAvailableChecks(available);

        if (available == 0) entity.setStatus(CheckbookStatus.AGOTADA);
        else entity.setStatus(
                request.getStatus() != null ? request.getStatus() : CheckbookStatus.ACTIVA
        );

        return repository.save(entity);
    }

    public Object deleteOrDisable(Long id, String motivo) {

        Checkbook entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("BNK-ERR-073: No encontrada"));

        if (entity.getUsedChecks() == 0) {
            entity.setStatus(CheckbookStatus.ANULADA);
        } else {
            entity.setStatus(CheckbookStatus.BLOQUEADA);
        }

        return repository.save(entity);
    }

    public Object findAll() {
        return repository.findAll();
    }
}