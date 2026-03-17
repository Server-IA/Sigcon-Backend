package com.sigcon.backend.assets.niif_alerts.domain.repository;

import com.sigcon.backend.assets.niif_alerts.domain.model.NiifCorrection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NiifCorrectionRepository extends JpaRepository<NiifCorrection, Long> {
}