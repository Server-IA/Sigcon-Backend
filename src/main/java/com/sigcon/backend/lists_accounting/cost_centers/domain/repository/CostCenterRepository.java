package com.sigcon.backend.lists_accounting.cost_centers.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.lists_accounting.cost_centers.domain.model.CostCenter;

@Repository
public interface CostCenterRepository extends JpaRepository<CostCenter, Long>, JpaSpecificationExecutor<CostCenter> {

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByNameAndDeletedAtIsNull(String name);

    boolean existsByCodeAndIdNotAndDeletedAtIsNull(String code, Long id);

    boolean existsByNameAndIdNotAndDeletedAtIsNull(String name, Long id);

    Optional<CostCenter> findByIdAndDeletedAtIsNull(Long id);
}
