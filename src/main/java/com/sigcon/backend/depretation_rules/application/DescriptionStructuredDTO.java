package com.sigcon.backend.depretation_rules.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Descripción estructurada con la base normativa y técnica de las reglas de depreciación")
public class DescriptionStructuredDTO {

    @Schema(description = "Base de cálculo utilizada para la depreciación", example = "Costo histórico del activo menos valor residual")
    @NotBlank(message = "La base de calculo es obligatoria")
    private String calculationBase;

    @Schema(description = "Parámetros técnicos aplicados al método de depreciación",example = "Tasa fija anual del 20% sobre el valor en libros")
    @NotBlank(message = "Los parametros son  obligatorios")
    private String parameters; 

    @Schema(description = "Excepciones o casos especiales a la regla (campo opcional)", example = "No aplica para activos adquiridos en el último trimestre del año",nullable = true)
    private String exception; 

     @Schema(description = "Norma contable o legal que sustenta la regla", example = "NIC 16 - Propiedades, Planta y Equipo")
    @NotBlank(message = "La norma aplicable es obligatoria")
    private String applicableNorm; 
    
}
