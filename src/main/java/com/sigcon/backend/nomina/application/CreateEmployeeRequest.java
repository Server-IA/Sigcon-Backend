package com.sigcon.backend.nomina.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request para crear/actualizar un empleado (HU-NOM-01).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request para registrar o actualizar un empleado (HU-NOM-01)")
public class CreateEmployeeRequest {

    @Schema(description = "FK a third_parties si el empleado es tambien tercero", example = "15")
    private Long thirdPartyId;

    @NotBlank(message = "documentType es obligatorio")
    @Schema(description = "Tipo de documento",
            allowableValues = {"CC", "CE", "TI", "PAS", "NIT"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String documentType;

    @NotBlank(message = "documentNumber es obligatorio")
    @Size(max = 50)
    @Schema(description = "Numero de documento", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "1020304050")
    private String documentNumber;

    @NotBlank(message = "fullName es obligatorio")
    @Size(max = 200)
    @Schema(description = "Nombre completo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fullName;

    @Size(max = 150)
    @Schema(description = "Cargo", example = "Contadora Senior")
    private String position;

    @Schema(description = "Tipo de contrato",
            allowableValues = {"INDEFINIDO", "FIJO", "OBRA_LABOR", "PRESTACION_SERVICIOS"})
    private String contractType;

    @NotNull(message = "baseSalary es obligatorio")
    @DecimalMin(value = "0.01", message = "baseSalary debe ser mayor a cero")
    @Schema(description = "Salario base mensual. Debe ser >= SMLV vigente (HU-NOM-01 E2)",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "3500000.00")
    private BigDecimal baseSalary;

    @Schema(description = "Fecha de ingreso", example = "2024-02-15")
    private LocalDate hireDate;

    @Size(max = 150)
    @Schema(description = "EPS. Obligatoria para liquidar (HU-NOM-03 E3)")
    private String eps;

    @Size(max = 150)
    @Schema(description = "Fondo de pension. Obligatorio para liquidar")
    private String pensionFund;

    @Size(max = 150)
    private String arl;

    @Size(max = 150)
    private String compensationBox;

    @Schema(description = "FK a cost_centers para imputar gastos de nomina")
    private Long costCenterId;

    @Schema(description = "Solo para UPDATE: motivo del cambio salarial si baseSalary cambia (HU-NOM-01 E3)",
            example = "Ajuste por reconocimiento al cumplimiento de metas del semestre")
    @Size(max = 500)
    private String salaryChangeReason;
}
