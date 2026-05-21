package com.sigcon.backend.audit.domain.repository;

import com.sigcon.backend.audit.domain.model.LogIntegrityExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * BNK-HU-065: acceso a la bitacora de ejecuciones del verificador de integridad.
 */
public interface LogIntegrityExecutionRepository extends JpaRepository<LogIntegrityExecution, Long> {

    List<LogIntegrityExecution> findTop50ByOrderByExecutedAtDesc();
}
