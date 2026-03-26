package com.sigcon.backend.parametrization.resources.domain.repository;

import com.sigcon.backend.parametrization.resources.domain.model.TypeRegimen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TypeRegimenRepository extends JpaRepository<TypeRegimen, Long>, JpaSpecificationExecutor<TypeRegimen> {
}
