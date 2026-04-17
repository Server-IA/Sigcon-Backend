package com.sigcon.backend.assets.niif_alerts.domain.repository;

import com.sigcon.backend.assets.niif_alerts.domain.model.AssetAnnualReview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Repositorio para revisiones anuales de activos (HU-ACT-12).
 */
public interface AssetAnnualReviewRepository
        extends JpaRepository<AssetAnnualReview, Long>, JpaSpecificationExecutor<AssetAnnualReview> {

    /**
     * Busca una revision existente para un activo en un anio fiscal especifico.
     * Usado para validar idempotencia (no duplicar revisiones).
     *
     * @param assetId    identificador del activo
     * @param fiscalYear anio fiscal de la revision
     * @return revision existente si ya fue registrada
     */
    Optional<AssetAnnualReview> findByAssetIdAndFiscalYearAndDeletedAtIsNull(Long assetId, Integer fiscalYear);
}
