package com.sigcon.backend.lists_accounting.cost_centers.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.lists_accounting.cost_centers.domain.model.CostCenter;

@Repository
public interface CostCenterRepository extends JpaRepository<CostCenter, Long>, JpaSpecificationExecutor<CostCenter> {

    boolean existsByCodeAndCompanyIdAndDeletedAtIsNull(String code, Long companyId);

    boolean existsByNameAndCompanyIdAndDeletedAtIsNull(String name, Long companyId);

    boolean existsByCodeAndCompanyIdAndIdNotAndDeletedAtIsNull(String code, Long companyId, Long id);

    boolean existsByNameAndCompanyIdAndIdNotAndDeletedAtIsNull(String name, Long companyId, Long id);

    Optional<CostCenter> findByIdAndDeletedAtIsNull(Long id);
}
