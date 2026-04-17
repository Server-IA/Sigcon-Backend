package com.sigcon.backend.vouchers.domain.repository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sigcon.backend.vouchers.domain.models.VoucherTypesEntity;
import com.sigcon.backend.vouchers.domain.models.VouchersEntity;

public interface VoucherRepository extends JpaRepository<VouchersEntity, Long>, JpaSpecificationExecutor<VouchersEntity> {

    @Query(value = """
        SELECT MAX(v.number) FROM vouchers v
        WHERE v.voucher_type_id = :voucherTypeId AND v.deleted_at IS NULL
    """, nativeQuery = true)
    BigInteger findTopByVoucherTypeIdAndDeletedAtIsNullOrderByNumberDesc(@Param("voucherTypeId") Long voucherTypeId);

    @Query(value = """
        SELECT v.* FROM vouchers v 
        WHERE v.asset_id = :assetId AND v.deleted_at IS NULL
    """, nativeQuery = true)
    List<VouchersEntity> findAllByAssetIdAndDeletedAtIsNull(Long assetId);

    @Query(value = """
        SELECT SUM(v.amount) FROM vouchers v 
        WHERE v.bank_account_id = :bankAccountId AND v.deleted_at IS NULL
    """, nativeQuery = true)
    BigDecimal sumVouchersByBankAccountId(Long bankAccountId);

    @Query(value = """
        SELECT SUM(v.amount) FROM vouchers v
        LEFT JOIN checks c ON c.id = v.check_id
        LEFT JOIN checkbooks cb ON cb.id = c.checkbooks_id
        WHERE c.id = :checkId AND v.deleted_at IS NULL
    """, nativeQuery = true)
    BigDecimal sumVouchersByCheckbookId(Long checkId);

    @Query("SELECT v FROM VouchersEntity v WHERE v.deletedAt IS NULL AND v.bankAccount.id = :bankAccountId "
            + "AND v.date >= :from AND v.date <= :to ORDER BY v.date DESC, v.id DESC")
    List<VouchersEntity> findReconciliationCandidates(
            @Param("bankAccountId") Long bankAccountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query(value = """
            SELECT COALESCE(SUM(v.amount), 0) FROM vouchers v
            WHERE v.bank_account_id = :bankAccountId
            AND v.deleted_at IS NULL AND v.date <= :asOfDate
            """, nativeQuery = true)
    BigDecimal sumVoucherAmountsByBankAccountUpToDate(
            @Param("bankAccountId") Long bankAccountId,
            @Param("asOfDate") LocalDate asOfDate);

}
