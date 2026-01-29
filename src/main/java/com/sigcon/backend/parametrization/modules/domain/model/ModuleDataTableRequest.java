package com.sigcon.backend.parametrization.modules.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class ModuleDataTableRequest {
    // ===== DataTables =====
    private int draw;
    private int start;
    private int length;

    // ===== Filtros =====
    private String name;
    private String description;
    private String url;
    private String icon;
    private Integer position;
    private String status;
}

