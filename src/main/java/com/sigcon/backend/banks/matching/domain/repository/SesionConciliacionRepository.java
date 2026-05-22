package com.sigcon.backend.banks.matching.domain.repository;

import com.sigcon.backend.banks.matching.domain.model.SesionConciliacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** BNK-HU-066/067/075/077: acceso a sesiones de conciliación (firma + versionado). */
public interface SesionConciliacionRepository extends JpaRepository<SesionConciliacion, Long> {

    Optional<SesionConciliacion> findByIdAndDeletedAtIsNull(Long id);

    List<SesionConciliacion> findByBankAccountIdAndDeletedAtIsNullOrderByIdDesc(Long bankAccountId);

    /** Sec 12: conciliaciones archivadas (soft-delete a 1 año) de la cuenta. */
    List<SesionConciliacion> findByBankAccountIdAndDeletedAtIsNotNullOrderByIdDesc(Long bankAccountId);

    /** HU-075 E8: cadena de versiones (la original + sus reaperturas). */
    List<SesionConciliacion> findBySesionOrigenIdAndDeletedAtIsNullOrderByVersionAsc(Long sesionOrigenId);
}
