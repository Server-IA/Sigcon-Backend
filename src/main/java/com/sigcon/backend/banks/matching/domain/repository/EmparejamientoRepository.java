package com.sigcon.backend.banks.matching.domain.repository;

import com.sigcon.backend.banks.matching.domain.model.Emparejamiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmparejamientoRepository extends JpaRepository<Emparejamiento, Long> {

    Optional<Emparejamiento> findByIdAndDeletedAtIsNull(Long id);

    List<Emparejamiento> findByCuentaBancariaIdAndDeletedAtIsNullOrderByIdDesc(Long cuentaBancariaId);

    List<Emparejamiento> findByCuentaBancariaIdAndEstadoAndDeletedAtIsNull(Long cuentaBancariaId, String estado);

    /**
     * QA Conciliación (2026-05-25) Bug 1: emparejamientos acotados a una sesión.
     * El Paso 5 (Aceptar/Rechazar) usa este filtro para no arrastrar emparejamientos
     * de otra sesión de la misma cuenta.
     */
    List<Emparejamiento> findByReconciliationSessionIdAndDeletedAtIsNullOrderByIdDesc(Long reconciliationSessionId);
}
