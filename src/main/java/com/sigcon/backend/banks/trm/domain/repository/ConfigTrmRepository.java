package com.sigcon.backend.banks.trm.domain.repository;

import com.sigcon.backend.banks.trm.domain.model.ConfigTrm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfigTrmRepository extends JpaRepository<ConfigTrm, Long> {
    Optional<ConfigTrm> findByCompanyId(Long companyId);
}
