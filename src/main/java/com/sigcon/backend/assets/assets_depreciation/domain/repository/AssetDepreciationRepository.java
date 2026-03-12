package com.sigcon.backend.assets.assets_depreciation.domain.repository;

import com.sigcon.backend.assets.assets_depreciation.domain.model.AssetDepreciation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository de acceso a datos para el histórico de depreciaciones.
 *
 * <p>Extiende {@link JpaRepository} para operaciones CRUD estándar y agrega
 * consultas específicas del dominio para recuperar el historial por activo o
 * por período contable.</p>
 */
public interface AssetDepreciationRepository extends JpaRepository<AssetDepreciation, Long> {

    /**
     * Retorna todas las depreciaciones registradas para un activo específico,
     * ordenadas por fecha de cálculo descendente (más reciente primero).
     *
     * @param assetId ID del activo
     * @return lista de registros históricos del activo
     */
    List<AssetDepreciation> findByAssetIdOrderByCalculationDateDesc(Long assetId);

    /**
     * Retorna todos los registros históricos de un período contable.
     *
     * @param depreciationPeriod período en formato YYYY-MM
     * @return lista de todos los activos depreciados en el período
     */
    List<AssetDepreciation> findByDepreciationPeriodOrderByAssetIdAsc(String depreciationPeriod);
}
