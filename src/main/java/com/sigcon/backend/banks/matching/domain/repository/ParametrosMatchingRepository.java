package com.sigcon.backend.banks.matching.domain.repository;

import com.sigcon.backend.banks.matching.domain.model.ParametrosMatching;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * BNK-HU-072: acceso a parámetros de matching (global por empresa + override por cuenta).
 */
public interface ParametrosMatchingRepository extends JpaRepository<ParametrosMatching, Long> {

    Optional<ParametrosMatching> findByCuentaBancariaIdIsNull();

    Optional<ParametrosMatching> findByCuentaBancariaId(Long cuentaBancariaId);
}
