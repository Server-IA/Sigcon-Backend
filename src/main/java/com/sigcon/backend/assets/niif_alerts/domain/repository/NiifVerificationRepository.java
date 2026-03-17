package com.sigcon.backend.assets.niif_alerts.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.sigcon.backend.assets.niif_alerts.domain.model.NiifVerification;

public interface NiifVerificationRepository extends JpaRepository<NiifVerification, Long> {
}
