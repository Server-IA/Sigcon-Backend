package com.sigcon.backend.banks.matching.domain.repository;

import com.sigcon.backend.banks.matching.domain.model.PartidaConciliatoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * BNK-HU-061 / HU-073: acceso a las partidas conciliatorias. Las consultas
 * heredan el filtro de tenant (TenantFilterAspect) por estar la entidad anotada
 * con @Filter("tenantFilter").
 */
public interface PartidaConciliatoriaRepository extends JpaRepository<PartidaConciliatoria, Long> {

    Optional<PartidaConciliatoria> findByIdAndDeletedAtIsNull(Long id);

    Optional<PartidaConciliatoria> findByFinancialMovementIdAndDeletedAtIsNull(Long financialMovementId);

    List<PartidaConciliatoria> findByBankAccountIdAndDeletedAtIsNullOrderByIdDesc(Long bankAccountId);

    List<PartidaConciliatoria> findByBankAccountIdAndEstadoAndDeletedAtIsNullOrderByIdDesc(Long bankAccountId, String estado);

    /** BNK-HU-074: todas las partidas en un estado para el tenant actual (job/reporte/dashboard). */
    List<PartidaConciliatoria> findByEstadoAndDeletedAtIsNullOrderByIdDesc(String estado);
}
