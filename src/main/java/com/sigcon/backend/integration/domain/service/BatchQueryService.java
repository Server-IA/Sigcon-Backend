package com.sigcon.backend.integration.domain.service;

import com.sigcon.backend.integration.application.IntegrationBatchListItemDTO;
import com.sigcon.backend.integration.application.IntegrationTransferDTO;
import com.sigcon.backend.integration.domain.model.IntegrationBatch;
import com.sigcon.backend.integration.domain.model.IntegrationTransfer;
import com.sigcon.backend.integration.domain.model.enums.BatchStatus;
import com.sigcon.backend.integration.domain.model.enums.TransferStatus;
import com.sigcon.backend.integration.domain.repository.IntegrationBatchRepository;
import com.sigcon.backend.integration.domain.repository.IntegrationTransferRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HU-INT-RF-14: consultas de lotes y transfers para el frontend de monitoreo.
 *
 * <p>Expone busqueda paginada con filtros (fecha, sistema origen, estado, solo
 * fallidos), consulta de detalle con los transfers asociados y descarga del
 * payload JSON original.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchQueryService {

    private final IntegrationBatchRepository batchRepository;
    private final IntegrationTransferRepository transferRepository;

    /**
     * HU-INT-RF-14 E1/E4: lista paginada de lotes con filtros opcionales.
     *
     * @param sourceSystemId filtro por sistema origen (exacto, puede ser null)
     * @param status filtro por BatchStatus (puede ser null)
     * @param fromDate filtro por receivedAt >= fromDate (puede ser null)
     * @param toDate filtro por receivedAt <= toDate (puede ser null)
     * @param onlyWithFailed si true, retorna solo lotes con al menos 1 transfer FAILED
     * @param page pagina 0-indexed
     * @param size tamaño de pagina (max 100)
     */
    public Map<String, Object> listBatches(String sourceSystemId, BatchStatus status,
                                            LocalDate fromDate, LocalDate toDate,
                                            boolean onlyWithFailed, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "receivedAt"));

        Specification<IntegrationBatch> spec = (root, query, cb) -> {
            List<Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (sourceSystemId != null && !sourceSystemId.isBlank()) {
                predicates.add(cb.equal(root.get("sourceSystemId"), sourceSystemId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("receivedAt"), fromDate.atStartOfDay()));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("receivedAt"), toDate.atTime(23, 59, 59)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<IntegrationBatch> pageResult = batchRepository.findAll(spec, pageable);

        List<IntegrationBatchListItemDTO> items = pageResult.getContent().stream()
                .map(b -> IntegrationBatchListItemDTO.from(b, countFailed(b.getId())))
                .filter(dto -> !onlyWithFailed || dto.getFailedDocuments() > 0)
                .collect(Collectors.toList());

        Map<String, Object> resp = new HashMap<>();
        resp.put("content", items);
        resp.put("page", pageResult.getNumber());
        resp.put("size", pageResult.getSize());
        resp.put("totalElements", pageResult.getTotalElements());
        resp.put("totalPages", pageResult.getTotalPages());
        return resp;
    }

    /**
     * HU-INT-RF-14 E2: detalle de un lote con sus transfers.
     */
    public Map<String, Object> getBatchDetail(Long batchId) {
        IntegrationBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado"));
        List<IntegrationTransfer> transfers =
                transferRepository.findByBatch_IdAndDeletedAtIsNull(batchId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", batch.getId());
        resp.put("exchangeId", batch.getExchangeId());
        resp.put("standardVersion", batch.getStandardVersion());
        resp.put("sourceSystemId", batch.getSourceSystemId());
        resp.put("sourceSystemName", batch.getSourceSystemName());
        resp.put("status", batch.getStatus() != null ? batch.getStatus().name() : null);
        resp.put("totalDocuments", batch.getTotalDocuments());
        resp.put("totalInvoices", batch.getTotalInvoices());
        resp.put("totalTransactions", batch.getTotalTransactions());
        // Nota: totalPayroll fue removido - bloque payroll AAEF desestimado del alcance
        resp.put("receivedAt", batch.getReceivedAt());
        resp.put("processedAt", batch.getProcessedAt());
        resp.put("ackSentAt", batch.getAckSentAt());
        resp.put("ackRetryCount", batch.getAckRetryCount());
        resp.put("errorMessage", batch.getErrorMessage());
        resp.put("transfers", transfers.stream()
                .map(IntegrationTransferDTO::from)
                .collect(Collectors.toList()));
        return resp;
    }

    /** HU-INT-RF-14 E3: recupera el payload JSON original del lote. */
    public String getPayloadJson(Long batchId) {
        IntegrationBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado"));
        return batch.getPayloadJson();
    }

    private int countFailed(Long batchId) {
        return (int) transferRepository.findByBatch_IdAndDeletedAtIsNull(batchId).stream()
                .filter(t -> t.getTransferStatus() == TransferStatus.FAILED)
                .count();
    }
}
