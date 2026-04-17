package com.sigcon.backend.nomina.domain.repository;

import com.sigcon.backend.nomina.domain.model.EmployeeSalaryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Historial salarial del empleado (HU-NOM-01 E3).
 */
public interface EmployeeSalaryHistoryRepository extends JpaRepository<EmployeeSalaryHistory, Long> {

    /** Historial completo de un empleado ordenado de mas reciente a mas antiguo. */
    List<EmployeeSalaryHistory> findByEmployeeIdAndDeletedAtIsNullOrderByEffectiveDateDesc(Long employeeId);
}
