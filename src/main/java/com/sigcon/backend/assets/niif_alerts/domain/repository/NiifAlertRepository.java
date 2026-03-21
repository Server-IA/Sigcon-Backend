package com.sigcon.backend.assets.niif_alerts.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.sigcon.backend.assets.niif_alerts.domain.model.NiifAlert;

public interface NiifAlertRepository extends JpaRepository<NiifAlert, Long> {
}