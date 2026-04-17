package com.sigcon.backend.nomina.application;

import com.sigcon.backend.nomina.domain.model.Employee;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Empleado del modulo de nomina (HU-NOM-01).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Empleado de nomina (HU-NOM-01)")
public class EmployeeDTO {

    @Schema(description = "ID interno", example = "42")
    private Long id;

    @Schema(description = "FK a third_parties si el empleado tambien es tercero", example = "15")
    private Long thirdPartyId;

    @Schema(description = "Tipo de documento",
            allowableValues = {"CC", "CE", "TI", "PAS", "NIT"}, example = "CC")
    private String documentType;

    @Schema(description = "Numero de documento", example = "1020304050")
    private String documentNumber;

    @Schema(description = "Nombre completo", example = "Maria Perez Ramirez")
    private String fullName;

    @Schema(description = "Cargo", example = "Contadora Senior")
    private String position;

    @Schema(description = "Tipo de contrato",
            allowableValues = {"INDEFINIDO", "FIJO", "OBRA_LABOR", "PRESTACION_SERVICIOS"},
            example = "INDEFINIDO")
    private String contractType;

    @Schema(description = "Salario base mensual en COP", example = "3500000.00")
    private BigDecimal baseSalary;

    @Schema(description = "Fecha de ingreso", example = "2024-02-15")
    private LocalDate hireDate;

    @Schema(description = "Fecha de terminacion", example = "null")
    private LocalDate terminationDate;

    @Schema(description = "EPS a la que esta afiliado el empleado", example = "Sura EPS")
    private String eps;

    @Schema(description = "Fondo de pension", example = "Porvenir")
    private String pensionFund;

    @Schema(description = "ARL (riesgos laborales)", example = "Colmena")
    private String arl;

    @Schema(description = "Caja de compensacion", example = "Colsubsidio")
    private String compensationBox;

    @Schema(description = "FK a cost_centers para imputar gastos", example = "3")
    private Long costCenterId;

    @Schema(description = "Estado", allowableValues = {"ACTIVE", "INACTIVE", "TERMINATED"},
            example = "ACTIVE")
    private String status;

    public static EmployeeDTO from(Employee e) {
        return EmployeeDTO.builder()
                .id(e.getId())
                .thirdPartyId(e.getThirdPartyId())
                .documentType(e.getDocumentType())
                .documentNumber(e.getDocumentNumber())
                .fullName(e.getFullName())
                .position(e.getPosition())
                .contractType(e.getContractType())
                .baseSalary(e.getBaseSalary())
                .hireDate(e.getHireDate())
                .terminationDate(e.getTerminationDate())
                .eps(e.getEps())
                .pensionFund(e.getPensionFund())
                .arl(e.getArl())
                .compensationBox(e.getCompensationBox())
                .costCenterId(e.getCostCenterId())
                .status(e.getStatus())
                .build();
    }
}
