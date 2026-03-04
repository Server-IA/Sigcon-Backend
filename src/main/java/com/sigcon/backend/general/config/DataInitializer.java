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
import lombok.Value;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Set;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;
import com.sigcon.backend.parametrization.menu.port.out.MenuRepositoryPort;
import com.sigcon.backend.parametrization.menuPermissions.application.MenuPermissionsDTO;
import com.sigcon.backend.parametrization.menuPermissions.domain.model.MenuPermissionsEntity;
import com.sigcon.backend.parametrization.menuPermissions.domain.repository.MenuPermissionsRepository;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;

import jakarta.transaction.Transactional;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
// @Profile("DEVELOPMENT")
@Transactional
public class DataInitializer implements CommandLineRunner {

        private final DataSource dataSource;

        private final RoleRepository roleRepository;
        private final PermissionRepository permissionRepository;
        private final ModuleRepository moduleRepository;
        private final MenuRepositoryPort menuRepositoryPort;
        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final MenuPermissionsRepository menuPermissionsRepository;

        @Override
        public void run(String... args) {

                // Crear módulos base para la aplicación
                Long moduleId = createOrUpdateModule("Parametrización", "Gestión de parámetros del sistema",
                                "parametrizacion", "bx-cog", 1);
                Long listsAccountingModuleId = createOrUpdateModule("Listas Contables",
                                "Gestión de listas contables", "lists-accounting", "bx-dollar", 2);

                // Crear menús base

                createOrUpdateMenu("Perfil", "perfil", "ri-user-line", 1, null, moduleId, "PERFIL");
                createOrUpdateMenu("Modulos", "modules", "ri-list-settings-fill", 2, null, moduleId, "MODULOS");
                createOrUpdateMenu("Menus", "menus", "ri-play-list-add-line", 3, null, moduleId, "MENUS");
                createOrUpdateMenu("Permisos", "permisos", "ri-menu-2-fill", 4, null, moduleId, "PERMISSIONS");
                createOrUpdateMenu("Roles", "roles", "ri-menu-2-fill", 5, null, moduleId, "ROLES");
                createOrUpdateMenu("Usuarios", "users", null, 6, null, moduleId, "USERS");
                createOrUpdateMenu("Parámetros", "parameters", null, 7, null, moduleId, "PARAMETERS");
                createOrUpdateMenu("Permisos Menu", "menu-permissions", null, 8, null, moduleId, "MENUSPERMISSIONS");
                
                // Listas Contables
                createOrUpdateMenu("Catalogo PUC", "puc-catalog", "ri-list-check", 1, null, listsAccountingModuleId, "PUC");
                createOrUpdateMenu("Reglas de Depreciación", "depreciation-rules", "ri-calculator-line", 2, null, listsAccountingModuleId, "DEPRECIATION_RULES");
                createOrUpdateMenu("Centros de Costo", "cost-centers", "ri-building-line", 3, null, listsAccountingModuleId, "COST_CENTERS");
                createOrUpdateMenu("Tasas de Cambio", "exchange-rates", "ri-exchange-dollar-line", 4, null, listsAccountingModuleId, "EXCHANGE_RATES");
                createOrUpdateMenu("Tipos de Moneda", "currency-types", "ri-money-dollar-circle-line", 5, null, listsAccountingModuleId, "CURRENCY_TYPES");
                createOrUpdateMenu("Cuentas Contables", "accounting-accounts", "ri-list-settings-fill", 6, null, listsAccountingModuleId, "ACCOUNTING_ACCOUNTS");

                // Crear permisos base
                Permission viewRoles = createPermission("Obtener roles", "Permiso para ver roles", TypePermits.READ,
                                "VIEW_ROLES", moduleId);
                Permission createRoles = createPermission("Crear roles", "Permiso para crear roles", TypePermits.CREATE,
                                "CREATE_ROLE", moduleId);
                Permission updateRole = createPermission("Actualizar roles", "Permiso para actualizar roles",
                                TypePermits.UPDATE, "UPDATE_ROLE", moduleId);
                Permission deleteRole = createPermission("Eliminar roles", "Permiso para eliminar roles",
                                TypePermits.DELETE,
                                "DELETE_ROLE", moduleId);
                Permission assignRole = createPermission("Asignar roles", "Permiso para asignar roles",
                                TypePermits.CREATE,
                                "ASSIGN_ROLE", moduleId);

                Permission viewUsers = createPermission("Obtener usuarios", "Permiso para ver usuarios",
                                TypePermits.READ,
                                "VIEW_USERS", moduleId);
                Permission createUsers = createPermission("Crear usuarios", "Permiso para crear usuarios",
                                TypePermits.CREATE,
                                "CREATE_USER", moduleId);
                Permission updateUser = createPermission("Actualizar usuarios", "Permiso para actualizar usuarios",
                                TypePermits.UPDATE, "UPDATE_USER", moduleId);
                Permission deleteUser = createPermission("Eliminar usuarios", "Permiso para eliminar usuarios",
                                TypePermits.DELETE, "DELETE_USER", moduleId);

                Permission createAccountingAccount = createPermission("Crear cuentas contables",
                                "Permiso para crear cuentas contables", TypePermits.CREATE, "CREATE_ACCOUNTING_ACCOUNT",
                                moduleId);
                Permission viewAccountingAccount = createPermission("Ver cuentas contables",
                                "Permiso para ver cuentas contables", TypePermits.READ, "VIEW_ACCOUNTING_ACCOUNT",
                                moduleId);
                Permission updateAccountingAccount = createPermission("Actualizar cuentas contables",
                                "Permiso para actualizar cuentas contables", TypePermits.UPDATE,
                                "UPDATE_ACCOUNTING_ACCOUNT", moduleId);
                Permission deleteAccountingAccount = createPermission("Eliminar cuentas contables",
                                "Permiso para eliminar cuentas contables", TypePermits.DELETE,
                                "DELETE_ACCOUNTING_ACCOUNT", moduleId);

                // Permission createUser = createPermission("Crear usuarios", "Permiso para
                // crear usuarios", TypePermits.CREATE, "CREATE_USER", moduleId);
                Permission createPermissionPerm = createPermission("Crear permisos", "Permiso para crear permisos",
                                TypePermits.CREATE, "CREATE_PERMISSION", moduleId);
                Permission updatePermissionPerm = createPermission("Actualizar permisos",
                                "Permiso para actualizar permisos",
                                TypePermits.UPDATE, "UPDATE_PERMISSION", moduleId);
                Permission viewPermissions = createPermission("Obtener permisos", "Permiso para ver permisos",
                                TypePermits.READ,
                                "VIEW_PERMISSIONS", moduleId);
                Permission assignPermission = createPermission("Asignar permisos", "Permiso para asignar permisos",
                                TypePermits.CREATE, "ASSIGN_PERMISSION", moduleId);
                Permission removePermission = createPermission("Eliminar permisos", "Permiso para eliminar permisos",
                                TypePermits.DELETE, "REMOVE_PERMISSION", moduleId);

                Permission createChartOfAccount = createPermission("Crear cuentas de contabilidad",
                                "Permiso para crear cuentas de contabilidad", TypePermits.CREATE,
                                "CREATE_CHART_OF_ACCOUNT", moduleId);
                Permission viewChartOfAccount = createPermission("Ver cuentas de contabilidad",
                                "Permiso para ver cuentas de contabilidad", TypePermits.READ, "VIEW_CHART_OF_ACCOUNT",
                                moduleId);
                Permission updateChartOfAccount = createPermission("Actualizar cuentas de contabilidad",
                                "Permiso para actualizar cuentas de contabilidad", TypePermits.UPDATE,
                                "UPDATE_CHART_OF_ACCOUNT", moduleId);
                Permission deleteChartOfAccount = createPermission("Eliminar cuentas de contabilidad",
                                "Permiso para eliminar cuentas de contabilidad", TypePermits.DELETE,
                                "DELETE_CHART_OF_ACCOUNT", moduleId);

                Permission viewMenus = createPermission("Ver menús", "Permiso para ver menús", TypePermits.READ,
                                "VIEW_MENUS",
                                moduleId);
                Permission createMenus = createPermission("Crear menús", "Permiso para crear menús", TypePermits.CREATE,
                                "CREATE_MENUS", moduleId);
                Permission updateMenus = createPermission("Actualizar menús", "Permiso para actualizar menús",
                                TypePermits.UPDATE, "UPDATE_MENUS", moduleId);
                Permission deleteMenus = createPermission("Eliminar menús", "Permiso para eliminar menús",
                                TypePermits.DELETE,
                                "DELETE_MENUS", moduleId);

                Permission createParameter = createPermission("Crear parámetros", "Permiso para crear parámetros",
                                TypePermits.CREATE, "CREATE_PARAMETER", moduleId);
                Permission viewParameter = createPermission("Ver parámetros", "Permiso para ver parámetros",
                                TypePermits.READ,
                                "VIEW_PARAMETER", moduleId);
                Permission updateParameter = createPermission("Actualizar parámetros",
                                "Permiso para actualizar parámetros",
                                TypePermits.UPDATE, "UPDATE_PARAMETER", moduleId);
                Permission deleteParameter = createPermission("Eliminar parámetros", "Permiso para eliminar parámetros",
                                TypePermits.DELETE, "DELETE_PARAMETER", moduleId);

                Permission viewMenuPermissions = createPermission("Ver permisos de menús",
                                "Permiso para ver permisos de menús",
                                TypePermits.READ, "VIEW_MENU_PERMISSIONS", moduleId);
                Permission createMenuPermissions = createPermission("Crear permisos de menús",
                                "Permiso para crear permisos de menús", TypePermits.CREATE, "CREATE_MENU_PERMISSIONS",
                                moduleId);
                Permission updateMenuPermissions = createPermission("Actualizar permisos de menús",
                                "Permiso para actualizar permisos de menús", TypePermits.UPDATE,
                                "UPDATE_MENU_PERMISSIONS", moduleId);
                Permission deleteMenuPermissions = createPermission("Eliminar permisos de menús",
                                "Permiso para eliminar permisos de menús", TypePermits.DELETE,
                                "DELETE_MENU_PERMISSIONS", moduleId);

                Permission viewCostCenters = createPermission("Ver centros de costo",
                                "Permiso para ver centros de costo",
                                TypePermits.READ, "VIEW_COST_CENTERS", listsAccountingModuleId);
                Permission createCostCenter = createPermission("Crear centros de costo",
                                "Permiso para crear centros de costo",
                                TypePermits.CREATE, "CREATE_COST_CENTER", listsAccountingModuleId);
                Permission updateCostCenter = createPermission("Actualizar centros de costo",
                                "Permiso para actualizar centros de costo", TypePermits.UPDATE, "UPDATE_COST_CENTER",
                                listsAccountingModuleId);
                Permission deleteCostCenter = createPermission("Eliminar centros de costo",
                                "Permiso para eliminar centros de costo", TypePermits.DELETE, "DELETE_COST_CENTER",
                                listsAccountingModuleId);
                
                //depretation_rules
                Permission viewDepretationRules = createPermission("Ver reglas de depreciación", 
                                "Permiso para ver reglas de depreciación", TypePermits.READ, 
                                "VIEW_DEPRECIATION_RULE", listsAccountingModuleId);
                Permission createDepretationRules = createPermission("Crear reglas de depreciación", 
                                "Permiso para crear reglas de depreciación", TypePermits.CREATE, 
                                "CREATE_DEPRECIATION_RULE", listsAccountingModuleId);
                Permission updateDepretationRules = createPermission("Actualizar reglas de depreciación", 
                                "Permiso para actualizar reglas de depreciación", TypePermits.UPDATE, 
                                "UPDATE_DEPRECIATION_RULE", listsAccountingModuleId);
                Permission deleteDepretationRules = createPermission("Eliminar reglas de depreciación", 
                                "Permiso para eliminar reglas de depreciación", TypePermits.DELETE, 
                                "DELETE_DEPRECIATION_RULE", listsAccountingModuleId);
          
                // Permisos Modulos
                Permission viewModules = createPermission("Ver módulos", "Permiso para ver módulos", TypePermits.READ,
                                "VIEW_MODULES", moduleId);
                Permission createModules = createPermission("Crear módulos", "Permiso para crear módulos", TypePermits.CREATE,
                                "CREATE_MODULES", moduleId);
                Permission updateModules = createPermission("Actualizar módulos", "Permiso para actualizar módulos", TypePermits.UPDATE,
                                "UPDATE_MODULES", moduleId);
                Permission deleteModules = createPermission("Eliminar módulos", "Permiso para eliminar módulos", TypePermits.DELETE,
                                "DELETE_MODULES", moduleId);
                Permission viewModulesMenu = createPermission("Ver menús de módulos", "Permiso para ver menús de módulos", TypePermits.READ,
                                "VIEW_MODULES_MENU", moduleId);

                // Exchange rates
                Permission viewExchangeRates = createPermission("Ver tasas de cambio",
                                "Permiso para visualizar tasas de cambio", TypePermits.READ,
                                "VIEW_EXCHANGE_RATE", listsAccountingModuleId);
                Permission createExchangeRates = createPermission("Crear tasa de cambio",
                                "Permiso para crear tasas de cambio", TypePermits.CREATE,
                                "CREATE_EXCHANGE_RATE", listsAccountingModuleId);
                Permission updateExchangeRates = createPermission("Actualizar tasa de cambio",
                                "Permiso para actualizar tasas de cambio", TypePermits.UPDATE,
                                "UPDATE_EXCHANGE_RATE", listsAccountingModuleId);
                Permission deleteExchangeRates = createPermission("Eliminar tasa de cambio",
                                "Permiso para eliminar tasas de cambio", TypePermits.DELETE,
                                "DELETE_EXCHANGE_RATE", listsAccountingModuleId);

                // Currency types
                Permission viewCurrencyType = createPermission("Ver tipos de moneda",
                                "Permiso para ver tipos de moneda", TypePermits.READ,
                                "VIEW_CURRENCY_TYPE", listsAccountingModuleId);
                Permission createCurrencyType = createPermission("Crear tipos de moneda",
                                "Permiso para crear tipos de moneda", TypePermits.CREATE,
                                "CREATE_CURRENCY_TYPE", listsAccountingModuleId);
                Permission updateCurrencyType = createPermission("Actualizar tipos de moneda",
                                "Permiso para actualizar tipos de moneda", TypePermits.UPDATE,
                                "UPDATE_CURRENCY_TYPE", listsAccountingModuleId);
                Permission deleteCurrencyType = createPermission("Eliminar tipos de moneda",
                                "Permiso para eliminar tipos de moneda", TypePermits.DELETE,
                                "DELETE_CURRENCY_TYPE", listsAccountingModuleId);

                // Permission createUser = createPermission("Crear usuarios", "Permiso para
                // crear usuarios", TypePermits.CREATE, "CREATE_USER", moduleId);

                // Crear roles y asignar permisos
                createOrUpdateRole("SUPERADMIN", new HashSet<>(Set.of(
                                viewRoles, createRoles, updateRole, deleteRole, assignRole,
                                viewUsers, createUsers, updateUser, deleteUser,
                                createPermissionPerm, updatePermissionPerm, viewPermissions, assignPermission,
                                removePermission,
                                createChartOfAccount, viewChartOfAccount, updateChartOfAccount, deleteChartOfAccount,
                                viewMenus, createMenus, updateMenus, deleteMenus,
                                createParameter, viewParameter, updateParameter, deleteParameter,
                                viewMenuPermissions, createMenuPermissions, updateMenuPermissions,
                                deleteMenuPermissions,
                                createAccountingAccount, viewAccountingAccount, updateAccountingAccount,
                                deleteAccountingAccount,
                                viewCostCenters, createCostCenter, updateCostCenter, deleteCostCenter,
                                viewExchangeRates, createExchangeRates, updateExchangeRates, deleteExchangeRates,
                                viewDepretationRules, createDepretationRules, updateDepretationRules, deleteDepretationRules,

                                viewCurrencyType, createCurrencyType, updateCurrencyType, deleteCurrencyType,
                                viewModules, createModules, updateModules, deleteModules, viewModulesMenu)));
                createOrUpdateRole("USER", new HashSet<>(Set.of()));

                // Crear usuarios

                createOrUpdateUser("SUPER", "ADMIN", "superadmin@gmail.com", "123456", "SUPERADMIN",
                                Set.of(
                                                viewRoles, createRoles, updateRole, deleteRole, assignRole,
                                                viewUsers, createUsers, updateUser, deleteUser,
                                                createPermissionPerm, updatePermissionPerm, viewPermissions,
                                                assignPermission, removePermission,
                                                createChartOfAccount, viewChartOfAccount,
                                                viewMenus, createMenus, updateMenus, deleteMenus,
                                                createParameter, viewParameter, updateParameter, deleteParameter,
                                                viewMenuPermissions, createMenuPermissions, updateMenuPermissions,
                                                deleteMenuPermissions,
                                                createAccountingAccount, viewAccountingAccount, updateAccountingAccount,
                                                deleteAccountingAccount,
                                                viewCostCenters, createCostCenter, updateCostCenter, deleteCostCenter,
                                                viewCurrencyType, createCurrencyType, updateCurrencyType,
                                                deleteCurrencyType, viewDepretationRules, createDepretationRules, updateDepretationRules, deleteDepretationRules,

                                viewModules, createModules, updateModules, deleteModules, viewModulesMenu)
                , "superadmin");

                createMenuPermissions("Perfil", "SUPERADMIN");
                createMenuPermissions("Perfil", "USER");

                createMenuPermissions("Modulos", "SUPERADMIN");
                createMenuPermissions("Menus", "SUPERADMIN");
                createMenuPermissions("Permisos", "SUPERADMIN");
                createMenuPermissions("Roles", "SUPERADMIN");
                createMenuPermissions("Usuarios", "SUPERADMIN");
                createMenuPermissions("Parámetros", "SUPERADMIN");
                createMenuPermissions("Permisos Menu", "SUPERADMIN");
                createMenuPermissions("Tasas de Cambio", "SUPERADMIN");

                createMenuPermissions("Catalogo PUC", "SUPERADMIN");
                createMenuPermissions("Reglas de Depreciación", "SUPERADMIN");
                createMenuPermissions("Centros de Costo", "SUPERADMIN");
                createMenuPermissions("Tasas de Cambio", "SUPERADMIN");
                createMenuPermissions("Tipos de Moneda", "SUPERADMIN");
                createMenuPermissions("Cuentas Contables", "SUPERADMIN");

                // ✅ Ejecutar SQL desde archivo

                
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                populator.addScript(new ClassPathResource("db/migration/V2__create_unique_indexes.sql"));
                // populator.addScript(new ClassPathResource("db/migration/V3__create_triggers.sql"));
                populator.execute(dataSource);

                System.out.println("Archivo SQL ejecutado correctamente");
        }

        private Permission createPermission(String name, String description, TypePermits type, String code,
                        Long moduleId) {
                return permissionRepository.findByName(name)
                                .orElseGet(() -> permissionRepository.save(
                                                Permission.builder()
                                                                .name(name)
                                                                .description(description)
                                                                .type(type)
                                                                .code(code)
                                                                .module(moduleRepository.findById(moduleId)
                                                                                .orElseThrow(() -> new RuntimeException(
                                                                                                "El módulo no existe")))
                                                                .build()));
        }

        private void createOrUpdateRole(String name, HashSet<Permission> permissions) {

                Role role = roleRepository.findByNameAndDeletedAtIsNull(name)
                                .orElseGet(() -> Role.builder().name(name).build());
                role.setPermissions(permissions);
                role.setStatus(Status.ACTIVE);
                roleRepository.save(role);
        }

        private Long createOrUpdateModule(String name, String description, String url, String icon, int position) {
                ModuleEntity module = moduleRepository.findByName(name)
                                .orElseGet(() -> ModuleEntity.builder()
                                                .name(name)
                                                .description(description)
                                                .url(url)
                                                .icon(icon)
                                                .position(position)
                                                .status(ModelStatus.ACTIVE)
                                                .build());
                moduleRepository.save(module);
                return module.getId();
        }

        private void createOrUpdateMenu(String label, String url, String icon, int menuOrder, Long parentId,
                        Long moduleId,
                        String component) {

                ModuleEntity module = moduleRepository.findById(moduleId)
                                .orElseThrow(() -> new RuntimeException("El módulo no existe"));
                MenuEntity menuEntity = parentId != null ? menuRepositoryPort.findById(parentId) : null;

                MenuEntity menu = menuRepositoryPort.findMenuByLabel(label)
                                .orElseGet(() -> MenuEntity.builder()
                                                .label(label)
                                                .icon(icon)
                                                .path(url)
                                                .menuOrder(menuOrder)
                                                .status(MenuStatus.ACTIVE)
                                                .component(component)
                                                .parent(menuEntity)
                                                .module(module)
                                                .build());

                menuRepositoryPort.saveMenu(menu);
        }

        private void createOrUpdateUser(String name, String lastname, String email, String password, String role,
                        Set<Permission> permissions, String username) {
                User user = userRepository.findByEmail(email)
                                .orElseGet(() -> User.builder()
                                                .name(name)
                                                .lastname(lastname)
                                                .email(email)
                                                .password(passwordEncoder.encode(password))
                                                .roles(Set.of(roleRepository.findByNameAndDeletedAtIsNull(role)
                                                                .orElseThrow(() -> new RuntimeException(
                                                                                "Role not found"))))
                                                .status(Status.ACTIVE)
                                                .username(username)
                                                .build());
                userRepository.save(user);
        }

        private void createMenuPermissions(String menuLabel, String roleName) {

                MenuEntity menu = menuRepositoryPort.findMenuByLabel(menuLabel)
                                .orElseThrow(() -> new RuntimeException("Menu not found"));

                Role role = roleRepository.findByNameAndDeletedAtIsNull(roleName)
                                .orElseThrow(() -> new RuntimeException("Role not found"));

                MenuPermissionsEntity menuPermissions = menuPermissionsRepository
                                .findByMenuAndRoleAndDeletedAtIsNull(menu, role)
                                .orElseGet(() -> MenuPermissionsEntity.builder()
                                                .menu(menu)
                                                .role(role)
                                                .build());

                menuPermissionsRepository.save(menuPermissions);
        }

}
