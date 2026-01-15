package com.sigcon.backend.config;

import com.sigcon.backend.users.domain.model.Permission;
import com.sigcon.backend.users.domain.model.Role;
import com.sigcon.backend.users.domain.repository.PermissionRepository;
import com.sigcon.backend.users.domain.repository.RoleRepository;
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



            // Crear roles y asignar permisos
            createRole("SUPERADMIN", Set.of(viewRoles, createRoles));

            createRole("USER", Set.of());



        };
    }

    private Permission createPermission(String name) {
        return permissionRepository.findByName(name)
                .orElseGet(() -> permissionRepository.save(
                        Permission.builder().name(name).build()
                ));
    }

    private void createRole(String name,Set<Permission> permissions) {
        roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(
                        Role.builder()
                                .name(name)
                                .permissions(permissions)
                                .build()
                ));
    }

}
