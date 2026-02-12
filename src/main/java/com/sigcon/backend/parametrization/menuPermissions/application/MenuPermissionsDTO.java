package com.sigcon.backend.parametrization.menuPermissions.application;

import java.time.LocalDateTime;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.users.application.role.RoleRequest;

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
    private Long menu_id;
    private Long role_id;
    private String menu;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
