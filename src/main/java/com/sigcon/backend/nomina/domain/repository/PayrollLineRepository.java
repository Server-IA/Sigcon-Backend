package com.sigcon.backend.nomina.domain.repository;

import com.sigcon.backend.nomina.domain.model.PayrollLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Lineas de concepto aplicadas a un recibo (HU-NOM-03).
 */
public interface PayrollLineRepository extends JpaRepository<PayrollLine, Long> {

    List<PayrollLine> findByReceiptIdAndDeletedAtIsNullOrderByLineOrder(Long receiptId);

    List<PayrollLine> findByReceiptIdInAndDeletedAtIsNull(List<Long> receiptIds);
}
