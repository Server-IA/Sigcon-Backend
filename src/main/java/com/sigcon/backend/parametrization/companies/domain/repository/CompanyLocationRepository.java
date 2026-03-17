package com.sigcon.backend.parametrization.companies.domain.repository;

import com.sigcon.backend.parametrization.companies.domain.model.Company;
import com.sigcon.backend.parametrization.companies.domain.model.CompanyLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyLocationRepository extends JpaRepository<CompanyLocation, Long> {

    List<CompanyLocation> findByCompanyAndDeletedAtIsNull(Company company);
}

