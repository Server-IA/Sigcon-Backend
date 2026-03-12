package com.sigcon.backend.third_parties.ecl_segmentation.application;

import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.RiskSegmentation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ManualAdjustmentRequest", description = "Solicitud de ajuste manual del segmento de riesgo ECL de un cliente (RF08 - Flujo 4,5,6,7)")
public class ManualAdjustmentRequest {

    @Schema(description = "Nuevo segmento de riesgo asignado manualmente al cliente", example = "MEDIUM", allowableValues = {"LOW", "MEDIUM", "HIGH"})
    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    private RiskSegmentation newSegmentation; //NUevo segmento de riesgo asignado manualmente al cliente
    @Schema(description = "Justificación del ajuste manual", example = "Cliente presenta mejora en su comportamiento de pago según reporte externo.", minLength = 50)
    @NotBlank(message = "Debe diligenciar todos los campos obligatorios")
    @Size(min = 50, message = "La justificacion debe de contar como minomo con 50 caracteres")
    private String justification; //Justificacion detallada del ajuste manual, explicando las razones y fundamentos para el cambio de segmento de riesgo.
}
