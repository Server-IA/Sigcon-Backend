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
         * TER-10: Verifica si un tercero esta referenciado como proveedor en algun activo.
         */
        boolean existsBySupplierId(Long supplierId);

        /**
         * HU-CFG-RF-16 E3: cuenta activos que usan la regla de depreciacion
         * (excluye soft-deleted). Si > 0 la regla NO puede eliminarse.
         */
        long countByDepretationRuleIdAndDeletedAtIsNull(Long depretationRuleId);

        /**
         * ACT-RF-02: Retorna activos candidatos para el cálculo de depreciación.
         * Criterios al nivel de query:
         * - status en {@code statuses} (ACTIVE o IN_REPAIR)
         * - depreciationRule no nulo
         * - no eliminado lógicamente
         *
         * <p>HU-ACT-02 E2 (QA 2026-05-05): la validacion de
         * {@code usefulLifeMonths > 0} se hace ahora dentro del bucle del
         * service para que los activos sin vida util definida queden
         * registrados en el listado <em>skipped</em> con motivo
         * INVALID_USEFUL_LIFE en lugar de desaparecer silenciosamente.
         */
        @Query("SELECT a FROM Assets a WHERE a.status IN :statuses " +
                        "AND a.depretationRule IS NOT NULL " +
                        "AND (a.deletedAt IS NULL)")
        List<Assets> findEligibleForDepreciation(
                        @Param("statuses") List<AssetStatus> statuses);
}
