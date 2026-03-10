package com.sigcon.backend.assets.assets.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetStatus;

import java.util.List;

public interface AssetsRepository extends JpaRepository<Assets, Long>, JpaSpecificationExecutor<Assets> {

        boolean existsByAssetCode(String assetCode);

        /**
         * ACT-RF-02: Retorna activos elegibles para el cálculo de depreciación.
         * Criterios:
         * - status en ACTIVE o IN_REPAIR
         * - usefulLifeMonths mayor a 0
         * - depreciationMethod no nulo
         * - no eliminado lógicamente
         */
        @Query("SELECT a FROM Assets a WHERE a.status IN :statuses " +
                        "AND a.usefulLifeMonths > 0 " +
                        "AND a.depreciationMethod IS NOT NULL " +
                        "AND (a.deletedAt IS NULL)")
        List<Assets> findEligibleForDepreciation(
                        @Param("statuses") List<AssetStatus> statuses);
}
