package com.sigcon.backend.parametrization.menuPermissions.application;

import java.time.LocalDateTime;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.users.domain.model.Role;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class MenuPermissionsDTO {

    private Long id;

    @NotNull(message = "El menú es requerido.")
    private Long menu_id;
    @NotNull(message = "El rol es requerido.")
    private Long role_id;
    
    private Menu menu;
    private Role role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
