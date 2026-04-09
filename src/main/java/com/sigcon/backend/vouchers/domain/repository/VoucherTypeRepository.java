package com.sigcon.backend.vouchers.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.vouchers.domain.models.VoucherTypesEntity;

public interface VoucherTypeRepository extends JpaRepository<VoucherTypesEntity, Long>, JpaSpecificationExecutor<VoucherTypesEntity> {

}
