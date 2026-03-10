package com.sigcon.backend.assets.domain.repository;

import com.sigcon.backend.third_parties.domain.model.ThirdParty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetThirdPartyBridgeRepository extends JpaRepository<ThirdParty, Long> {

    Optional<ThirdParty> findByIdAndDeletedAtIsNull(Long id);
}
