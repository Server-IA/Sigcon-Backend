package com.sigcon.backend.assets.assets.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;

import java.util.Optional;

public interface AssetThirdPartyBridgeRepository extends JpaRepository<ThirdParty, Long> {

    Optional<ThirdParty> findByIdAndDeletedAtIsNull(Long id);
}
