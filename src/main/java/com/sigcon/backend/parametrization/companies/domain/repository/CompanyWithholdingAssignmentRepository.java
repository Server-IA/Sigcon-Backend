package com.sigcon.backend.parametrization.companies.domain.repository;

import com.sigcon.backend.parametrization.companies.domain.model.Company;
import com.sigcon.backend.parametrization.companies.domain.model.CompanyWithholdingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyWithholdingAssignmentRepository extends JpaRepository<CompanyWithholdingAssignment, Long> {

    List<CompanyWithholdingAssignment> findByCompanyAndDeletedAtIsNull(Company company);

    boolean existsByCompanyAndWithholdingAndDeletedAtIsNull(Company company, com.sigcon.backend.parametrization.resources.domain.model.Withholding withholding);
}
