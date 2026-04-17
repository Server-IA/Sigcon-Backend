package com.sigcon.backend.third_parties.change_history.domain.service;

import com.sigcon.backend.third_parties.change_history.application.ChangeHistoryDTO;
import com.sigcon.backend.third_parties.change_history.domain.model.ThirdPartyChangeHistory;
import com.sigcon.backend.third_parties.change_history.domain.repository.ThirdPartyChangeHistoryRepository;
import com.sigcon.backend.utils.SuccessRespondJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Servicio para registrar y consultar el historial de cambios de terceros.
 * TER-03: Trazabilidad de modificaciones sobre campos clave del tercero.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThirdPartyChangeHistoryService {

    private final ThirdPartyChangeHistoryRepository repository;

    /**
     * Compara los valores anteriores y posteriores de un tercero y registra cada cambio detectado.
     *
     * @param thirdPartyId ID del tercero modificado
     * @param beforeValues mapa de valores anteriores (fieldName -> valor)
     * @param afterValues  mapa de valores posteriores (fieldName -> valor)
     * @param userId       ID del usuario que realizo la modificacion
     */
    public void trackChanges(Long thirdPartyId,
                             java.util.Map<String, String> beforeValues,
                             java.util.Map<String, String> afterValues,
                             Long userId) {
        for (String field : beforeValues.keySet()) {
            String oldVal = beforeValues.get(field);
            String newVal = afterValues.get(field);

            if (!Objects.equals(oldVal, newVal)) {
                ThirdPartyChangeHistory record = ThirdPartyChangeHistory.builder()
                        .thirdPartyId(thirdPartyId)
                        .fieldName(field)
                        .oldValue(oldVal)
                        .newValue(newVal)
                        .changedBy(userId)
                        .build();
                repository.save(record);
                log.info("Cambio registrado en tercero {}: {} [{}] -> [{}]",
                        thirdPartyId, field, oldVal, newVal);
            }
        }
    }

    /**
     * Obtiene el historial de cambios de un tercero.
     *
     * @param thirdPartyId ID del tercero
     * @return respuesta con la lista de cambios ordenada por fecha descendente
     */
    public ResponseEntity<?> getHistory(Long thirdPartyId) {
        List<ChangeHistoryDTO> history = repository
                .findByThirdPartyIdOrderByChangedAtDesc(thirdPartyId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Historial de cambios obtenido correctamente."),
                        Optional.of(history)));
    }

    /**
     * Convierte una entidad de historial a su DTO de respuesta.
     */
    private ChangeHistoryDTO toDto(ThirdPartyChangeHistory entity) {
        return ChangeHistoryDTO.builder()
                .id(entity.getId())
                .thirdPartyId(entity.getThirdPartyId())
                .fieldName(entity.getFieldName())
                .oldValue(entity.getOldValue())
                .newValue(entity.getNewValue())
                .changedBy(entity.getChangedBy())
                .changedAt(entity.getChangedAt())
                .build();
    }
}
