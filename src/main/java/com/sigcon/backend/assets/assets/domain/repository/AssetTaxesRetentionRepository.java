package com.sigcon.backend.assets.assets.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.assets.assets.domain.model.AssetsTaxesRetention;

public interface AssetTaxesRetentionRepository extends JpaRepository<AssetsTaxesRetention, Long>, JpaSpecificationExecutor<AssetsTaxesRetention> {

    List<AssetsTaxesRetention> findAllByAssetIdAndDeletedAtIsNull(Long assetId);
}
