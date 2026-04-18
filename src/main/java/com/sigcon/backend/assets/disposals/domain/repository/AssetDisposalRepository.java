package com.sigcon.backend.assets.disposals.domain.repository;

import com.sigcon.backend.assets.disposals.domain.model.AssetDisposal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio para la entidad AssetDisposal.
 * Soporta paginacion, filtros dinamicos y consultas personalizadas.
 */
public interface AssetDisposalRepository extends JpaRepository<AssetDisposal, Long>,
        JpaSpecificationExecutor<AssetDisposal> {

    /**
     * Verifica si existe una disposicion activa (no eliminada) para un activo.
     *
     * @param assetId identificador del activo
     * @return true si ya existe una disposicion vigente
     */
    boolean existsByAssetIdAndDeletedAtIsNull(Long assetId);
}
