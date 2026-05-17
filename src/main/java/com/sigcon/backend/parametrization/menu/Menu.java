package com.sigcon.backend.parametrization.menu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;
import com.sigcon.backend.parametrization.modules.application.ModuleDTO;
import com.sigcon.backend.parametrization.users.application.role.PermissionDTO;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class Menu {

    private Long id;
    @NotBlank(message = "El label es requerido")
    @Size(min = 3, max = 50, message = "El label debe tener entre 3 y 50 caracteres")
    private String label;
    private String icon;

    @NotBlank(message = "La ruta es requerida")
    @Size(min = 3, max = 50, message = "La ruta debe tener entre 3 y 50 caracteres")
    private String path;

    @NotNull(message = "El orden es requerido")
    @Min(value = 1, message = "El orden debe ser mayor a 0")
    private Integer menuOrder;

    private Long parentId;
    private Menu parent;

    @NotNull(message = "El módulo es requerido")
    private Long moduleId;
    private ModuleDTO module;
    
    private MenuStatus status;

    private Boolean visible;

    private String component;
    private String method;
    private String menuType;
    private List<PermissionDTO> permissions;
    private List<Menu> childrens;
    /**
     * QA Bloque AX (Bug #3, 2026-05-17): code del permiso requerido para que el
     * menu sea visible en el sidebar. NULL = publico (compat con menus sin
     * restriccion). MenuService.getMenusByModuleId filtra por este campo cuando
     * el user no es ADMIN ni PLATFORM_ADMIN. Persistido en menus.required_permission_code
     * (V9-Zzzzf).
     */
    private String requiredPermissionCode;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
