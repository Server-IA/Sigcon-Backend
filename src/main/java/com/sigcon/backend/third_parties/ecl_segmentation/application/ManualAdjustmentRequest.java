package com.sigcon.backend.third_parties.ecl_segmentation.application;

import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.RiskSegmentation;

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
public class ManualAdjustmentRequest {

    @NotNull(message = "Debe Diligenciar todos los campos obligatorios")
    private Long clientId; //Identificador unico (ID) del Cliente
    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    private RiskSegmentation newSegmentation; //NUevo segmento de riesgo asignado manualmente al cliente
    @NotBlank(message = "Debe diligenciar todos los campos obligatorios")
    @Size(min = 50, message = "La justificacion debe de contar como minomo con 50 caracteres")
    private String justification; //Justificacion detallada del ajuste manual, explicando las razones y fundamentos para el cambio de segmento de riesgo.
    
}
