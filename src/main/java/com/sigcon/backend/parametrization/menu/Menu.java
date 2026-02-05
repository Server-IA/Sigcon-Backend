package com.sigcon.backend.parametrization.menu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;
import com.sigcon.backend.parametrization.modules.domain.model.Module;

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
    private Menu parent;
    private Long moduleId;
    private Module module;
    private MenuStatus status;
    private String component;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
