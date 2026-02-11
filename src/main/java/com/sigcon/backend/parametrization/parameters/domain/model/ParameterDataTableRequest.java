package com.sigcon.backend.parametrization.parameters.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParameterDataTableRequest {

    // ===== DataTables =====
    private int draw;
    private int start;
    private int length;

    // ===== Filtros =====
    private String name;
    private String value;
    private String category;
    private Boolean active;
}
