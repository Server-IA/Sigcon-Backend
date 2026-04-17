package com.sigcon.backend.nomina.domain.repository;

import com.sigcon.backend.nomina.domain.model.RetentionBracket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Tabla de retencion en la fuente parametrizable (HU-NOM-03 E2, Art. 383 ET).
 */
public interface RetentionBracketRepository extends JpaRepository<RetentionBracket, Long> {

    /** Rangos del año gravable ordenados por UVT ascendente. */
    List<RetentionBracket> findByTaxYearAndDeletedAtIsNullOrderByUvtMinAsc(Integer taxYear);
}
