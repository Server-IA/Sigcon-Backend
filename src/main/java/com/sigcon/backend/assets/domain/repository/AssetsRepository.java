package com.sigcon.backend.assets.domain.repository;

import com.sigcon.backend.assets.domain.model.Assets;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AssetsRepository extends JpaRepository<Assets, Long>, JpaSpecificationExecutor<Assets> {

    boolean existsByAssetCode(String assetCode);
}
