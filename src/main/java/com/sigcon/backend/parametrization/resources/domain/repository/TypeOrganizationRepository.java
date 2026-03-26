package com.sigcon.backend.parametrization.resources.domain.repository;

import com.sigcon.backend.parametrization.resources.domain.model.TypeOrganization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TypeOrganizationRepository extends JpaRepository<TypeOrganization, Long>, JpaSpecificationExecutor<TypeOrganization> {
}
