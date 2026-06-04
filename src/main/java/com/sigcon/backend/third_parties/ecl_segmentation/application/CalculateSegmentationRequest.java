package com.sigcon.backend.third_parties.ecl_segmentation.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Schema(
    name = "CalculateSegmentationRequest",
    description = "Solicitud de cálculo automático del segmento de riesgo ECL de un cliente (RF08 - Flujos 1,2,3)"
)
public class CalculateSegmentationRequest { 

    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    @Schema(description = "Identificador único del cliente a segmentar", example = "1")
    private Long clientId; // Identificador único del cliente a segmentar

    // BUG TER-RF-08 (2026-06-03 / Imagen 4): el frontend envia la clave JSON
    // "isMonthlyClose", pero Jackson, al derivar la propiedad desde el getter
    // booleano isMonthlyClose() de Lombok, le quita el prefijo "is" y espera
    // "monthlyClose". Resultado: el valor del switch NUNCA llegaba (siempre
    // false) y el recalculo sobre un ajuste manual vigente siempre se bloqueaba
    // con HTTP 409. @JsonProperty fuerza a Jackson a aceptar la clave que envia
    // el frontend.
    @Builder.Default
    @JsonProperty("isMonthlyClose")
    @Schema(
        description = "Indica si el cálculo se ejecuta en el cierre mensual. " +
                      "Si es true sobreescribe ajustes manuales vigentes. " +
                      "Si es false y existe ajuste manual vigente, retorna HTTP 409",
        example = "false"
    )
    private boolean isMonthlyClose = false; // true = cierre mensual, false = cálculo puntual
}


