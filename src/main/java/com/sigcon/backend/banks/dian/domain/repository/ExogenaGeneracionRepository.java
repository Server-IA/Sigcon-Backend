package com.sigcon.backend.banks.dian.domain.repository;

import com.sigcon.backend.banks.dian.domain.model.ExogenaGeneracion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExogenaGeneracionRepository extends JpaRepository<ExogenaGeneracion, Long> {
    List<ExogenaGeneracion> findByOrderByGeneradoAtDesc();
    List<ExogenaGeneracion> findByFormatoOrderByGeneradoAtDesc(String formato);
}
