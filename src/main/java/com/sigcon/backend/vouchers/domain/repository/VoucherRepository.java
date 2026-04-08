package com.sigcon.backend.vouchers.domain.repository;

import java.math.BigInteger;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.sigcon.backend.parametrization.companies.domain.model.Company;
import com.sigcon.backend.vouchers.domain.models.VoucherTypesEntity;
import com.sigcon.backend.vouchers.domain.models.VouchersEntity;

public interface VoucherRepository extends JpaRepository<VouchersEntity, Long>, JpaSpecificationExecutor<VouchersEntity> {

    @Query(value = """
        SELECT MAX(v.number) FROM vouchers v 
        WHERE v.voucher_type_id = :voucherTypeId AND v.company_id = :companyId AND v.deleted_at IS NULL
    """, nativeQuery = true)
    BigInteger findTopByVoucherTypeIdAndCompanyIdOrderByNumberDesc(Long voucherTypeId, Long companyId);

    @Query(value = """
        SELECT v.* FROM vouchers v 
        WHERE v.asset_id = :assetId AND v.deleted_at IS NULL
    """, nativeQuery = true)
    List<VouchersEntity> findAllByAssetIdAndDeletedAtIsNull(Long assetId);

}
