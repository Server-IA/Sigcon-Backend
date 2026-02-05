package com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class MenuDataTableRequest {
    private int draw;
    private int start;
    private int length;

    private String label;
    private String description;
    private String url;
    private String icon;
    private Integer position;
    private String status;
    private Long moduleId;
    private Long parentId;
}
