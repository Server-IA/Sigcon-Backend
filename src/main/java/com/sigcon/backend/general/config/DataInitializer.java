package com.sigcon.backend.general.config;

import com.sigcon.backend.parametrization.modules.application.ModuleDTO;
import com.sigcon.backend.parametrization.modules.domain.model.ModuleEntity;
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
import com.sigcon.backend.parametrization.menuPermissions.application.MenuPermissionsDTO;
import com.sigcon.backend.parametrization.menuPermissions.domain.model.MenuPermissionsEntity;
import com.sigcon.backend.parametrization.menuPermissions.domain.repository.MenuPermissionsRepository;
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
    private final MenuPermissionsRepository menuPermissionsRepository;
    
    @Bean
    CommandLineRunner initData() {
        return args -> {

            // Crear módulos base para la aplicación
            Long moduleId = createOrUpdateModule("Parametrización", "Gestión de parámetros del sistema", "parametrizacion", "bx-cog", 1);
            

            // Crear menús base

            createOrUpdateMenu("Perfil", "perfil", "ri-user-line", 1, null, moduleId, "PERFIL");
            createOrUpdateMenu("Modulos", "modules", "ri-list-settings-fill", 2, null, moduleId, "MODULOS");
            createOrUpdateMenu("Menus", "menus", "ri-play-list-add-line", 3, null, moduleId, "MENUS");
            createOrUpdateMenu("Permisos", "permisos", "ri-menu-2-fill", 1, null, moduleId, "PERMISSIONS");
            createOrUpdateMenu("Roles", "roles", "ri-menu-2-fill", 1, null, moduleId, "ROLES");
            createOrUpdateMenu("Permisos Menu", "menu-permissions", null, 5, null, moduleId, "MENUSPERMISSIONS");

            // Crear permisos base
            Permission viewRoles = createPermission("Obtener roles", "Permiso para ver roles", TypePermits.READ, "VIEW_ROLES", moduleId);
            Permission createRoles = createPermission("Crear roles", "Permiso para crear roles", TypePermits.CREATE, "CREATE_ROLE", moduleId);
            Permission updateRole = createPermission("Actualizar roles", "Permiso para actualizar roles", TypePermits.UPDATE, "UPDATE_ROLE", moduleId);
            Permission deleteRole = createPermission("Eliminar roles", "Permiso para eliminar roles", TypePermits.DELETE, "DELETE_ROLE", moduleId);
            Permission assignRole = createPermission("Asignar roles", "Permiso para asignar roles", TypePermits.CREATE, "ASSIGN_ROLE", moduleId);
            Permission viewUsers = createPermission("Obtener usuarios", "Permiso para ver usuarios", TypePermits.READ, "VIEW_USERS", moduleId);
            Permission updateUser = createPermission("Actualizar usuarios", "Permiso para actualizar usuarios", TypePermits.UPDATE, "UPDATE_USER", moduleId);
            Permission deleteUser = createPermission("Eliminar usuarios", "Permiso para eliminar usuarios", TypePermits.DELETE, "DELETE_USER", moduleId);
            Permission createPermission = createPermission("Crear permisos", "Permiso para crear permisos", TypePermits.CREATE, "CREATE_PERMISSION", moduleId);
            Permission viewPermissions = createPermission("Obtener permisos", "Permiso para ver permisos", TypePermits.READ, "VIEW_PERMISSIONS", moduleId);
            Permission assignPermission = createPermission("Asignar permisos", "Permiso para asignar permisos", TypePermits.CREATE, "ASSIGN_PERMISSION", moduleId);
            Permission removePermission = createPermission("Eliminar permisos", "Permiso para eliminar permisos", TypePermits.DELETE, "REMOVE_PERMISSION", moduleId);
            Permission createChartOfAccount = createPermission("Crear cuentas de contabilidad", "Permiso para crear cuentas de contabilidad", TypePermits.CREATE, "CREATE_CHART_OF_ACCOUNT", moduleId);
            Permission viewChartOfAccount = createPermission("Ver cuentas de contabilidad", "Permiso para ver cuentas de contabilidad", TypePermits.READ, "VIEW_CHART_OF_ACCOUNT", moduleId);
            Permission viewMenus = createPermission("Ver menús", "Permiso para ver menús", TypePermits.READ, "VIEW_MENUS", moduleId);
            Permission createParameter = createPermission("Crear parámetros", "Permiso para crear parámetros", TypePermits.CREATE, "CREATE_PARAMETER", moduleId);
            Permission viewParameter = createPermission("Ver parámetros", "Permiso para ver parámetros", TypePermits.READ, "VIEW_PARAMETER", moduleId);
            Permission updateParameter = createPermission("Actualizar parámetros", "Permiso para actualizar parámetros", TypePermits.UPDATE, "UPDATE_PARAMETER", moduleId);
            Permission deleteParameter = createPermission("Eliminar parámetros", "Permiso para eliminar parámetros", TypePermits.DELETE, "DELETE_PARAMETER", moduleId);

            Permission viewMenuPermissions = createPermission("Ver permisos de menús", "Permiso para ver permisos de menús", TypePermits.READ, "VIEW_MENU_PERMISSIONS", moduleId);
            Permission createMenuPermissions = createPermission("Crear permisos de menús", "Permiso para crear permisos de menús", TypePermits.CREATE, "CREATE_MENU_PERMISSIONS", moduleId);
            Permission updateMenuPermissions = createPermission("Actualizar permisos de menús", "Permiso para actualizar permisos de menús", TypePermits.UPDATE, "UPDATE_MENU_PERMISSIONS", moduleId);
            Permission deleteMenuPermissions = createPermission("Eliminar permisos de menús", "Permiso para eliminar permisos de menús", TypePermits.DELETE, "DELETE_MENU_PERMISSIONS", moduleId);

            // Crear roles y asignar permisos
            createOrUpdateRole("SUPERADMIN", Set.of(viewRoles, createRoles, updateRole, deleteRole, assignRole, viewUsers, updateUser, deleteUser, createPermission, viewPermissions, assignPermission, removePermission, createChartOfAccount, viewChartOfAccount));
            createOrUpdateRole("SUPERADMIN", Set.of(viewRoles, createRoles, updateRole, deleteRole, assignRole, viewUsers, updateUser, deleteUser, createPermission, viewPermissions, assignPermission, removePermission, createChartOfAccount, viewChartOfAccount,
                viewMenus, createParameter, viewParameter, updateParameter, deleteParameter, viewMenuPermissions, createMenuPermissions, updateMenuPermissions, deleteMenuPermissions));
            createOrUpdateRole("USER", Set.of());

            // Crear usuarios

            createOrUpdateUser("SUPERADMIN", null, "superadmin@gmail.com", "123456", "SUPERADMIN", Set.of(viewRoles, createRoles, updateRole, deleteRole, assignRole, viewUsers, updateUser, deleteUser, createPermission, viewPermissions, assignPermission, removePermission, createChartOfAccount, viewChartOfAccount, viewMenus,
                createParameter, viewParameter, updateParameter, deleteParameter, viewMenuPermissions, createMenuPermissions, updateMenuPermissions, deleteMenuPermissions));

            createMenuPermissions(6L, 1L);

        };
    }

    private Permission createPermission(String name, String description, TypePermits type, String code, Long moduleId) {
        return permissionRepository.findByName(name)
                .orElseGet(() -> permissionRepository.save(
                        Permission.builder()
                        .name(name)
                        .description(description)
                        .type(type)
                        .code(code)
                        .module(moduleRepository.findById(moduleId).orElseThrow(() -> new RuntimeException("El módulo no existe")))
                        .build()
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
        ModuleEntity module = moduleRepository.findByName(name)
                .orElseGet(() -> ModuleEntity.builder()
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
        
        ModuleEntity module = moduleRepository.findById(moduleId).orElseThrow(() -> new RuntimeException("El módulo no existe"));

        MenuEntity menu = menuRepositoryPort.findMenuByLabel(label)
            .orElseGet(() -> MenuEntity.builder()
                .label(label)
                .icon(icon)
                .path(url)
                .menuOrder(menuOrder)
                .parent(parentId != null ? MenuEntity.builder()
                    .id(parentId)
                        .label(label)
                        .icon(icon)
                        .build()
                    : null)
                .module(module)
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

    private void createMenuPermissions(Long menu_id, Long role_id){
        MenuEntity menu = menuRepositoryPort.findById(menu_id);

        Role role = roleRepository.findById(role_id).orElseGet(null);

        MenuPermissionsEntity entity = MenuPermissionsEntity.builder()
            .menu(menu)
            .role(role)
            .build();

        menuPermissionsRepository.save(entity);
    }

}
