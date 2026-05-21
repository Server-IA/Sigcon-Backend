package com.sigcon.backend.banks.matching.domain.repository;

import com.sigcon.backend.banks.matching.domain.model.FirmaElectronica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** BNK-HU-066: acceso append-only a las firmas electrónicas. */
public interface FirmaElectronicaRepository extends JpaRepository<FirmaElectronica, Long> {

    Optional<FirmaElectronica> findByIdAndCompanyId(Long id, Long companyId);

    List<FirmaElectronica> findBySesionIdOrderByIdAsc(Long sesionId);
}
