package com.sigcon.backend.accounts_receivable.dian.resolutions.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sigcon.backend.accounts_receivable.dian.resolutions.domain.model.DianResolution;
import com.sigcon.backend.accounts_receivable.dian.resolutions.domain.model.DianResolutionStatus;

/**
 * Repositorio de resoluciones DIAN.
 */
public interface DianResolutionRepository
        extends JpaRepository<DianResolution, Long>, JpaSpecificationExecutor<DianResolution> {

    boolean existsByResolutionNumberAndDeletedAtIsNull(String resolutionNumber);

    boolean existsByResolutionNumberAndIdNotAndDeletedAtIsNull(String resolutionNumber, Long id);

    Optional<DianResolution> findByIdAndDeletedAtIsNull(Long id);

    List<DianResolution> findByStatusAndDeletedAtIsNull(DianResolutionStatus status);

    /**
     * Busca la resolucion activa vigente para un prefijo en una fecha dada.
     */
    @Query("SELECT r FROM DianResolution r WHERE r.prefix = :prefix AND r.status = 'ACTIVE' "
         + "AND r.startDate <= :date AND r.endDate >= :date "
         + "AND r.currentNumber < r.endNumber AND r.deletedAt IS NULL ORDER BY r.endDate ASC")
    List<DianResolution> findActiveByPrefixAndDate(@Param("prefix") String prefix,
                                                   @Param("date") LocalDate date);
}
