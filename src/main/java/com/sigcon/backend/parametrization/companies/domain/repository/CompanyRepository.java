package com.sigcon.backend.parametrization.companies.domain.repository;

import com.sigcon.backend.parametrization.companies.domain.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CompanyRepository extends JpaRepository<Company, Long>, JpaSpecificationExecutor<Company> {

    boolean existsByNitAndDvAndDeletedAtIsNull(String nit, String dv);

    boolean existsByNitAndDvAndIdNotAndDeletedAtIsNull(String nit, String dv, Long id);

    boolean existsByNameAndDeletedAtIsNull(String name);

    boolean existsByNameAndIdNotAndDeletedAtIsNull(String name, Long id);
}

