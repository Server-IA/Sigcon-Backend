package com.sigcon.backend.parametrization.resources.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.parametrization.resources.domain.model.PaymentTerms;

public interface PaymentTermsRepository extends JpaRepository<PaymentTerms, Long>, JpaSpecificationExecutor<PaymentTerms> {

}
