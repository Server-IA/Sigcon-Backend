package com.sigcon.backend.parametrization.menu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;
import com.sigcon.backend.parametrization.modules.domain.model.Module;
import com.sigcon.backend.parametrization.users.application.role.PermissionDTO;

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
    private List<PermissionDTO> permissions;
    private List<Menu> childrens;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
