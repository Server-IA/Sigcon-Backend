package com.sigcon.backend.parametrization.resources.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.parametrization.resources.domain.model.Country;

import java.util.Optional;

public interface CountryRepository extends JpaRepository<Country, Long>, JpaSpecificationExecutor<Country> {
    Optional<Country> findByCodeIgnoreCase(String code);
}