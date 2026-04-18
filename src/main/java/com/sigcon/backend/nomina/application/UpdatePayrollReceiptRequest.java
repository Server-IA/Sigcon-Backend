package com.sigcon.backend.nomina.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HU-NOM-04 E2: Request para actualizar campos editables de un recibo en DRAFT.
 *
 * <p>Solo permite cambiar {@code daysWorked} y {@code notes}. Los totales no
 * son editables directamente. Si el recibo esta en APPROVED o CLOSED, el
 * servicio rechaza la operacion con el mensaje exacto del Excel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Actualizar campos editables de un recibo (solo DRAFT) - HU-NOM-04 E2")
public class UpdatePayrollReceiptRequest {

    @Min(value = 1, message = "daysWorked debe ser >= 1")
    @Max(value = 31, message = "daysWorked debe ser <= 31")
    @Schema(description = "Dias trabajados del periodo", example = "28")
    private Integer daysWorked;

    @Size(max = 500)
    @Schema(description = "Notas libres del recibo", example = "Ajuste por licencia no remunerada")
    private String notes;
}
