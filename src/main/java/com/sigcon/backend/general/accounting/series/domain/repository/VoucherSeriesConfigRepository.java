package com.sigcon.backend.general.accounting.series.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.general.accounting.series.domain.model.VoucherSeriesConfig;

/**
 * Repositorio JPA para {@link VoucherSeriesConfig}.
 * HU-CG-03A E3/E5: configuracion de series por tipo de comprobante.
 */
public interface VoucherSeriesConfigRepository extends JpaRepository<VoucherSeriesConfig, Long> {

    Optional<VoucherSeriesConfig> findByVoucherTypeAndDeletedAtIsNull(String voucherType);

    List<VoucherSeriesConfig> findAllByDeletedAtIsNullOrderByVoucherTypeAsc();

    boolean existsByVoucherTypeAndDeletedAtIsNull(String voucherType);

    boolean existsByVoucherTypeAndIdNotAndDeletedAtIsNull(String voucherType, Long id);
}
