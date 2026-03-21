package com.sigcon.backend.banks.checks.domain.repository;

import com.sigcon.backend.banks.checks.domain.model.Check;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface CheckRepository extends JpaRepository<Check, Long>, JpaSpecificationExecutor<Check> {

    boolean existsByNumberCheckAndDeletedAtIsNull(Integer numberCheck);

    Optional<Check> findByIdAndDeletedAtIsNull(Long id);
}
