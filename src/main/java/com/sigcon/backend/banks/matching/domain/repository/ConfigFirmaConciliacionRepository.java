package com.sigcon.backend.banks.matching.domain.repository;

import com.sigcon.backend.banks.matching.domain.model.ConfigFirmaConciliacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** BNK-HU-066 E1: configuración de firma por empresa (una fila por tenant). */
public interface ConfigFirmaConciliacionRepository extends JpaRepository<ConfigFirmaConciliacion, Long> {

    Optional<ConfigFirmaConciliacion> findByCompanyId(Long companyId);
}
