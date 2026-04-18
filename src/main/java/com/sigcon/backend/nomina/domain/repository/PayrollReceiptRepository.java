package com.sigcon.backend.nomina.domain.repository;

import com.sigcon.backend.nomina.domain.model.PayrollReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de recibos de nomina (HU-NOM-03/04).
 */
public interface PayrollReceiptRepository extends JpaRepository<PayrollReceipt, Long>, JpaSpecificationExecutor<PayrollReceipt> {

    /** Evita liquidar dos veces el mismo empleado/periodo. */
    Optional<PayrollReceipt> findByEmployeeIdAndPeriodYearAndPeriodMonthAndDeletedAtIsNull(
            Long employeeId, Integer year, Integer month);

    boolean existsByEmployeeIdAndPeriodYearAndPeriodMonthAndDeletedAtIsNull(
            Long employeeId, Integer year, Integer month);

    /** Recibos del periodo para reportes (PILA, resumen contable). */
    List<PayrollReceipt> findByPeriodYearAndPeriodMonthAndDeletedAtIsNull(Integer year, Integer month);

    /** Recibos del empleado en un rango de meses (para liquidacion definitiva). */
    List<PayrollReceipt> findByEmployeeIdAndPeriodYearAndDeletedAtIsNull(Long employeeId, Integer year);
}
