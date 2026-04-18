package com.sigcon.backend.nomina.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * HU-NOM-03: Request para liquidar la nomina de un grupo de empleados para
 * un periodo especifico.
 *
 * <p>Si {@code employeeIds} es null o vacio, se liquidan TODOS los empleados
 * activos. Si {@code costCenterId} esta definido, se filtra por centro de
 * costo. Los dos filtros son excluyentes: si se envia employeeIds tiene
 * prioridad.
 *
 * <p>Las {@code extras} opcionales permiten agregar por empleado horas extra,
 * bonificaciones u otros conceptos manuales que NO se calculan automaticamente
 * desde el salario base.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Liquidar nomina del periodo para un grupo de empleados (HU-NOM-03)")
public class LiquidatePayrollRequest {

    @NotNull(message = "year es obligatorio")
    @Min(2000) @Max(2100)
    @Schema(description = "Año del periodo", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026")
    private Integer year;

    @NotNull(message = "month es obligatorio")
    @Min(1) @Max(12)
    @Schema(description = "Mes del periodo (1-12)", requiredMode = Schema.RequiredMode.REQUIRED, example = "4")
    private Integer month;

    @Schema(description = "Tipo de periodo", allowableValues = {"MONTHLY", "BIWEEKLY"}, example = "MONTHLY")
    @Builder.Default
    private String periodType = "MONTHLY";

    @Schema(description = "Dias trabajados (default 30)", example = "30")
    @Builder.Default
    private Integer daysWorked = 30;

    @Schema(description = "IDs de empleados a liquidar. Null/vacio = todos los ACTIVE")
    private List<Long> employeeIds;

    @Schema(description = "Filtrar por centro de costo. Ignorado si employeeIds esta presente")
    private Long costCenterId;

    @Schema(description = "Extras opcionales por empleado (horas extras, bonif, etc.)")
    private List<ExtraLine> extras;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Concepto extra manual para un empleado especifico")
    public static class ExtraLine {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "42")
        private Long employeeId;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "HORAS_EXTRA")
        private String conceptCode;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "150000.00")
        private BigDecimal amount;
    }
}
