package com.sigcon.backend.parametrization.menu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class Menu {

    private Long id;
    private String label;
    private String icon;
    private String path;
    private Integer menuOrder;
    private Long parentId;
    private Long moduleId;
    private MenuStatus status;
    private List<Menu> childrens = new ArrayList<>();
    private String component;
}
