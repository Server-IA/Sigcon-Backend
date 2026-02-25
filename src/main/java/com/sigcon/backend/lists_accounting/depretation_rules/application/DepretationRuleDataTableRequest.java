package com.sigcon.backend.lists_accounting.depretation_rules.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationStatus;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationType;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Filtros y parámetros de paginación para consultar las reglas de depreciación")
public class DepretationRuleDataTableRequest {
    
    // Filtros para búsqueda
    @Schema(description = "Filtro por nombre de la regla (búsqueda parcial)", example = "Equipos")
    private String name;
    @Schema(description = "Filtro por tipo de depreciacion", example = "LINEAR", allowableValues = {"LINEAR","DECREASING","ACCELERATED","PRODUCTION_UNITS","MINIMUN_USEFUL_LIFE"})
    private DepretationType depretationType;
    @Schema(description = "Filtro por estado de la regla ", example = "ACTIVE", allowableValues = {"ACTIVE","INACTIVE"})
    private DepretationStatus status;
    @Schema(description = "Fecha de creación desde, formato yyyy-MM-dd", example = "2024-01-01")
    private LocalDate createdAtFrom;
    @Schema(description = "Fecha de creación hasta, formato yyyy-MM-dd", example = "2024-12-31")
    private LocalDate createdAtTo;
    
    // Paginación (si se usa sin DataTableRequest)
    @Schema(description = "Número de página (0-based)", example = "0")
    private int page;
    @Schema(description = "Cantidad de registros por página", example = "10")
    private int size;
}
