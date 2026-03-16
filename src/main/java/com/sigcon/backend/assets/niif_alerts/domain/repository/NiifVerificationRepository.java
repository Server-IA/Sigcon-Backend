package com.sigcon.backend.assets.niif_alerts.domain.repository;

import com.sigcon.backend.assets.niif_alerts.domain.model.NiifVerification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NiifVerificationRepository extends JpaRepository<NiifVerification, Long> {
}
