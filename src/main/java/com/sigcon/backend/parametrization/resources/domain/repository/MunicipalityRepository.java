package com.sigcon.backend.parametrization.resources.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.parametrization.resources.domain.model.Municipality;

import java.util.List;
import java.util.Optional;

public interface MunicipalityRepository extends JpaRepository<Municipality, Long>, JpaSpecificationExecutor<Municipality> {
    Optional<Municipality> findByCodeIgnoreCase(String code);
    Optional<Municipality> findByNameIgnoreCase(String name);

    List<Municipality> findByCountryId(Long countryId);
}