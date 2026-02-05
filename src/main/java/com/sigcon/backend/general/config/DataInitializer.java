package com.sigcon.backend.general.config;

import com.sigcon.backend.parametrization.modules.domain.model.Module;
import com.sigcon.backend.parametrization.modules.domain.model.enums.ModelStatus;
import com.sigcon.backend.parametrization.modules.domain.repository.ModuleRepository;

import com.sigcon.backend.parametrization.users.domain.model.Permission;
import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.model.enums.Status;
import com.sigcon.backend.parametrization.users.domain.model.enums.TypePermits;
import com.sigcon.backend.parametrization.users.domain.repository.PermissionRepository;
import com.sigcon.backend.parametrization.users.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;
import com.sigcon.backend.parametrization.menu.port.out.MenuRepositoryPort;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final ModuleRepository moduleRepository;
    private final MenuRepositoryPort menuRepositoryPort;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Bean
    CommandLineRunner initData() {
        return args -> {

            // Crear módulos base para la aplicación
            Long moduleId = createOrUpdateModule("Parametrización", "Gestión de parámetros del sistema", "parametrizacion", "bx-cog", 1);
            

            // Crear menús base

            createOrUpdateMenu("Modulos", "modules", "ri-function-ai-line", 1, null, moduleId, "MODULOS");
            createOrUpdateMenu("Menus", "menus", "ri-play-list-add-line", 2, null, moduleId, "MENUS");

            // Crear permisos base
            Permission viewRoles = createPermission("VIEW_ROLES", "Permiso para ver roles", TypePermits.READ);
            Permission createRoles = createPermission("CREATE_ROLE", "Permiso para crear roles", TypePermits.CREATE);
            Permission updateRole = createPermission("UPDATE_ROLE", "Permiso para actualizar roles", TypePermits.UPDATE);
            Permission deleteRole = createPermission("DELETE_ROLE", "Permiso para eliminar roles", TypePermits.DELETE);
            Permission assignRole = createPermission("ASSIGN_ROLE", "Permiso para asignar roles", TypePermits.CREATE);
            Permission viewUsers = createPermission("VIEW_USERS", "Permiso para ver usuarios", TypePermits.READ);
            Permission updateUser = createPermission("UPDATE_USER", "Permiso para actualizar usuarios", TypePermits.UPDATE);
            Permission deleteUser = createPermission("DELETE_USER", "Permiso para eliminar usuarios", TypePermits.DELETE);
            Permission createPermission = createPermission("CREATE_PERMISSION", "Permiso para crear permisos", TypePermits.CREATE);
            Permission viewPermissions = createPermission("VIEW_PERMISSIONS", "Permiso para ver permisos", TypePermits.READ);
            Permission assignPermission = createPermission("ASSIGN_PERMISSION", "Permiso para asignar permisos", TypePermits.CREATE);
            Permission removePermission = createPermission("REMOVE_PERMISSION", "Permiso para eliminar permisos", TypePermits.DELETE);
            Permission createChartOfAccount = createPermission("CREATE_CHART_OF_ACCOUNT", "Permiso para crear cuentas de contabilidad", TypePermits.CREATE);
            Permission viewChartOfAccount = createPermission("VIEW_CHART_OF_ACCOUNT", "Permiso para ver cuentas de contabilidad", TypePermits.READ);




            // Crear roles y asignar permisos
            createOrUpdateRole("SUPERADMIN", Set.of(viewRoles, createRoles, updateRole, deleteRole, assignRole, viewUsers, updateUser, deleteUser, createPermission, viewPermissions, assignPermission, removePermission, createChartOfAccount, viewChartOfAccount));
            createOrUpdateRole("USER", Set.of());

            // Crear usuarios

            createOrUpdateUser("SUPERADMIN", null, "superadmin@gmail.com", "123456", "SUPERADMIN", Set.of(viewRoles, createRoles, updateRole, deleteRole, assignRole, viewUsers, updateUser, deleteUser, createPermission, viewPermissions, assignPermission, removePermission, createChartOfAccount, viewChartOfAccount));



        };
    }

    private Permission createPermission(String name, String description, TypePermits type) {
        return permissionRepository.findByName(name)
                .orElseGet(() -> permissionRepository.save(
                        Permission.builder().name(name).description(description).type(type).build()
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

    private Long createOrUpdateModule(String name, String description, String url, String icon, int position) {
        Module module = moduleRepository.findByName(name)
                .orElseGet(() -> Module.builder()
                        .name(name)
                        .url(url)
                        .position(position)
                        .status(ModelStatus.ACTIVE)
                        .build());
        module.setDescription(description);
        module.setIcon(icon);
        module.setUrl(url);
        module.setPosition(position);
        if (module.getStatus() == null) {
            module.setStatus(ModelStatus.ACTIVE);
        }
        moduleRepository.save(module);
        return module.getId();
    }

    private void createOrUpdateMenu(String label, String url, String icon, int menuOrder, Long parentId, Long moduleId, String component) {
        MenuEntity menu = menuRepositoryPort.findMenuByLabel(label)
            .orElseGet(() -> MenuEntity.builder()
                .label(label)
                .icon(icon)
                .path(url)
                .menuOrder(menuOrder)
                .parentId(parentId)
                .moduleId(moduleId)
                .status(MenuStatus.ACTIVE)
                .component(component)
                .build());
    
        menuRepositoryPort.saveMenu(menu);
    }

    private void createOrUpdateUser(String name, String lastname, String email, String password, String role, Set<Permission> permissions) {
        User user = userRepository.findByEmail(email)
            .orElseGet(() -> User.builder()
                .name(name)
                .lastname(lastname)
                .email(email)
                .password(passwordEncoder.encode(password))
                .roles(Set.of(roleRepository.findByName(role).orElseThrow(() -> new RuntimeException("Role not found"))))
                .status(Status.ACTIVE)
                .build());
        userRepository.save(user);
    }

}
