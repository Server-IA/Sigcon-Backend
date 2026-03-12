package com.sigcon.backend.assets.assets_depreciation.domain.services;

import com.sigcon.backend.assets.assets_depreciation.application.ViewAssetDepreciationDTO;
import com.sigcon.backend.assets.assets_depreciation.domain.model.AssetDepreciation;
import com.sigcon.backend.assets.assets_depreciation.domain.repository.AssetDepreciationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ACT-RF-02 — Servicio de consulta del histórico de depreciaciones.
 *
 * <p>Permite recuperar el historial de depreciaciones de un activo específico
 * o de todos los activos en un período contable determinado.</p>
 *
 * <p>Este servicio es de solo lectura: no modifica ni elimina registros.</p>
 */
@Service
@RequiredArgsConstructor
public class AssetDepreciationHistoryService {

    private final AssetDepreciationRepository assetDepreciationRepository;

    /**
     * Retorna el historial completo de depreciaciones de un activo,
     * ordenado por fecha de cálculo descendente (más reciente primero).
     *
     * @param assetId ID del activo a consultar
     * @return lista de registros históricos del activo
     */
    public List<ViewAssetDepreciationDTO> findByAssetId(Long assetId) {
        return assetDepreciationRepository
                .findByAssetIdOrderByCalculationDateDesc(assetId)
                .stream()
                .map(this::toViewDTO)
                .toList();
    }

    /**
     * Retorna todos los registros históricos de depreciación correspondientes
     * a un período contable (formato YYYY-MM).
     *
     * @param period período contable en formato YYYY-MM
     * @return lista de registros del período
     */
    public List<ViewAssetDepreciationDTO> findByPeriod(String period) {
        return assetDepreciationRepository
                .findByDepreciationPeriodOrderByAssetIdAsc(period)
                .stream()
                .map(this::toViewDTO)
                .toList();
    }

    // ─── Helper privado ──────────────────────────────────────────────────────

    private ViewAssetDepreciationDTO toViewDTO(AssetDepreciation record) {
        return ViewAssetDepreciationDTO.builder()
                .id(record.getId())
                .assetId(record.getAsset().getId())
                .assetCode(record.getAsset().getAssetCode())
                .assetName(record.getAsset().getAssetName())
                .depreciationPeriod(record.getDepreciationPeriod())
                .previousBookValue(record.getPreviousBookValue())
                .currentBookValue(record.getCurrentBookValue())
                .depreciationAmount(record.getDepreciationAmount())
                .depretationType(record.getDepretationType())
                .calculationDate(record.getCalculationDate())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
