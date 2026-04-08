package com.sigcon.backend.parametrization.resources.domain.repository;

import com.sigcon.backend.parametrization.companies.domain.model.Company;
import com.sigcon.backend.parametrization.resources.domain.model.Withholding;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WithholdingRepository extends JpaRepository<Withholding, Long>, JpaSpecificationExecutor<Withholding> {

}
