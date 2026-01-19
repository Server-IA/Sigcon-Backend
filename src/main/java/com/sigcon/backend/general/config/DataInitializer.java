package com.sigcon.backend.general.config;

import com.sigcon.backend.parametrization.users.domain.model.Permission;
import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.parametrization.users.domain.model.enums.Status;
import com.sigcon.backend.parametrization.users.domain.repository.PermissionRepository;
import com.sigcon.backend.parametrization.users.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Bean
    CommandLineRunner initData() {
        return args -> {

            // Crear permisos base
            Permission viewRoles = createPermission("VIEW_ROLES");
            Permission createRoles = createPermission("CREATE_ROLE");
            Permission updateRole = createPermission("UPDATE_ROLE");
            Permission deleteRole = createPermission("DELETE_ROLE");
            Permission assignRole = createPermission("ASSIGN_ROLE");
            Permission viewUsers = createPermission("VIEW_USERS");
            Permission updateUser = createPermission("UPDATE_USER");
            Permission deleteUser = createPermission("DELETE_USER");
            Permission createPermission = createPermission("CREATE_PERMISSION");
            Permission viewPermissions = createPermission("VIEW_PERMISSIONS");
            Permission assignPermission = createPermission("ASSIGN_PERMISSION");
            Permission removePermission = createPermission("REMOVE_PERMISSION");
            Permission createChartOfAccount = createPermission("CREATE_CHART_OF_ACCOUNT");




            // Crear roles y asignar permisos
            createOrUpdateRole("SUPERADMIN", Set.of(viewRoles, createRoles, updateRole, deleteRole, assignRole, viewUsers, updateUser, deleteUser, createPermission, viewPermissions, assignPermission, removePermission, createChartOfAccount));
            createOrUpdateRole("USER", Set.of());



        };
    }

    private Permission createPermission(String name) {
        return permissionRepository.findByName(name)
                .orElseGet(() -> permissionRepository.save(
                        Permission.builder().name(name).build()
                ));
    }

    private void createOrUpdateRole(String name, Set<Permission> permissions) {

        Role role = roleRepository.findByName(name).orElseGet(() -> Role.builder().name(name).build());
        role.setPermissions(permissions);


        if (role.getStatus() == null) {
            role.setStatus(Status.ACTIVE);
        }

        roleRepository.save(role);
    }


}
