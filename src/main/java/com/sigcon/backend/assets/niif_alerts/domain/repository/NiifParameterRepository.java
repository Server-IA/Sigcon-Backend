package com.sigcon.backend.assets.niif_alerts.domain.repository;

import com.sigcon.backend.assets.niif_alerts.domain.model.NiifParameter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NiifParameterRepository extends JpaRepository<NiifParameter, Long> {

    Optional<NiifParameter> findByAssetCategory(String assetCategory);

}