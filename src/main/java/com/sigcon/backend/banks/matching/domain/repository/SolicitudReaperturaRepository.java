package com.sigcon.backend.banks.matching.domain.repository;

import com.sigcon.backend.banks.matching.domain.model.SolicitudReapertura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** BNK-HU-075: acceso a las solicitudes de reapertura. */
public interface SolicitudReaperturaRepository extends JpaRepository<SolicitudReapertura, Long> {

    Optional<SolicitudReapertura> findByIdAndDeletedAtIsNull(Long id);

    List<SolicitudReapertura> findBySesionIdAndDeletedAtIsNullOrderByIdDesc(Long sesionId);

    List<SolicitudReapertura> findByEstadoAndDeletedAtIsNullOrderByIdDesc(String estado);
}
