package com.sigcon.backend.banks.matching.domain.repository;

import com.sigcon.backend.banks.matching.domain.model.EmparejamientoDetalle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmparejamientoDetalleRepository extends JpaRepository<EmparejamientoDetalle, Long> {

    List<EmparejamientoDetalle> findByEmparejamientoId(Long emparejamientoId);

    List<EmparejamientoDetalle> findByFinancialMovementId(Long financialMovementId);

    void deleteByEmparejamientoId(Long emparejamientoId);
}
