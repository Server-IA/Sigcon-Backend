package com.sigcon.backend.nomina.application;

import com.sigcon.backend.nomina.domain.model.EmployeeSalaryHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entrada del historial salarial de un empleado (HU-NOM-01 E3).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Cambio salarial historico (append-only) - HU-NOM-01 E3")
public class EmployeeSalaryHistoryDTO {
    private Long id;
    private Long employeeId;
    private BigDecimal previousSalary;
    private BigDecimal newSalary;
    private LocalDate effectiveDate;
    private String reason;
    private String changedBy;
    private LocalDateTime createdAt;

    public static EmployeeSalaryHistoryDTO from(EmployeeSalaryHistory h) {
        return EmployeeSalaryHistoryDTO.builder()
                .id(h.getId())
                .employeeId(h.getEmployeeId())
                .previousSalary(h.getPreviousSalary())
                .newSalary(h.getNewSalary())
                .effectiveDate(h.getEffectiveDate())
                .reason(h.getReason())
                .changedBy(h.getChangedBy())
                .createdAt(h.getCreatedAt())
                .build();
    }
}
