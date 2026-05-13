package com.sigcon.backend.parametrization.modules.domain.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestBody;

import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.SpringDataMenuRepository;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;
import com.sigcon.backend.parametrization.menu.service.MenuService;
import com.sigcon.backend.parametrization.modules.application.ModuleDTO;
import com.sigcon.backend.parametrization.modules.domain.model.ModuleDataTableRequest;
import com.sigcon.backend.parametrization.modules.domain.model.ModuleEntity;
import com.sigcon.backend.parametrization.modules.domain.model.enums.ModelStatus;
import com.sigcon.backend.parametrization.modules.domain.repository.ModuleRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.parametrization.settings.domain.service.NavSettingsService;
import com.sigcon.backend.platform.tenant.TenantContext;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ModuleRepository moduleRepository;
    private final MenuService menuService;
    private final SpringDataMenuRepository menuRepository;
    private final AuditPublisher auditPublisher;
    private final NavSettingsService navSettingsService;
    private final UserRepository userRepository;
    private final com.sigcon.backend.parametrization.temporary_permissions.domain.service.TemporaryPermissionService temporaryPermissionService;
    private final DataTableSpecificationBuilder<ModuleEntity> moduleSpecificationBuilder =
        new DataTableSpecificationBuilder<>();

    public ResponseEntity<?> getModulesPaged(DataTableRequest request) {
        try {

            int start = Math.max(0, request.getStart());
            int length = request.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<ModuleEntity> spec = moduleSpecificationBuilder.build(request)
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

            Page<ModuleEntity> modules = moduleRepository.findAll(spec, pageable);
    
            return ResponseEntity.ok(
                DataTableResponse.from(modules.map(module -> ModuleDTO.builder()
                    .id(module.getId())
                    .name(module.getName())
                    .description(module.getDescription())
                    .url(module.getUrl())
                    .icon(module.getIcon())
                    .position(module.getPosition())
                    .status(module.getStatus())
                    .menus(
                        null
                    )
                    .build()), request.getDraw())
            );
    
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
            );
        }
    }

    public ResponseEntity<?> getModules(ModuleDTO request) {
        try {
            List<ModuleEntity> modules = moduleRepository.findAllByStatus(ModelStatus.ACTIVE);
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Módulos obtenidos correctamente"), Optional.of(modules))
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al obtener los módulos"))
            );
        }
    }

    /**
     * Devuelve los modulos a los que el usuario autenticado tiene acceso.
     *
     * <p>Logica:
     * <ul>
     *   <li>ADMIN y SUPERADMIN: ven todos los modulos activos.</li>
     *   <li>Otros roles: solo ven modulos donde tengan al menos UN permiso.
     *       La autoridad {@code PERM_VIEW_MODULES_MENU} funciona como "pase libre"
     *       si un rol quiere ver todo el menu sin otorgar permisos por modulo.</li>
     * </ul>
     */
    public ResponseEntity<?> getModulesMenu() {
        try {
            List<ModuleEntity> modules = moduleRepository.findActiveModulesWithActiveMenus(
                    parseStatus(ModelStatus.ACTIVE), parseStatus(MenuStatus.ACTIVE));

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a ->
                    "ROLE_ADMIN".equals(a.getAuthority()) ||
                    "ROLE_SUPERADMIN".equals(a.getAuthority()));
            boolean isPlatformAdmin = auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> "PLATFORM_ADMIN".equals(a.getAuthority()));

            // Bloque F: el modulo "Plataforma" solo se muestra a PLATFORM_ADMIN.
            // Los admins de empresa (con ROLE_ADMIN pero sin PLATFORM_ADMIN) NO lo ven.
            // Bloque AM (2026-05-03): un PLATFORM_ADMIN ademas debe ver el modulo
            // "Parametrizacion" porque dentro vive la configuracion GLOBAL del
            // sistema (Modulos, Menus, Permisos, Paises, Municipios, Navegacion,
            // Notificaciones por rol). Sin acceso a Parametrizacion la cuenta de
            // plataforma no podia administrar la plataforma misma; ademas las
            // rutas /parametrizacion/* devolvian 403 al CatchAllRoute por no
            // estar en los modulos del menu.
            java.util.function.Predicate<ModuleEntity> platformVisibility = m -> {
                boolean isPlatformModule = "Plataforma".equalsIgnoreCase(m.getName())
                        || "platform".equalsIgnoreCase(m.getUrl());
                boolean isParametrizacion = m.getId() != null && m.getId().equals(1L);
                if (isPlatformAdmin) {
                    return isPlatformModule || isParametrizacion;
                }
                return !isPlatformModule;
            };
            modules = modules.stream().filter(platformVisibility).toList();

            final List<ModuleEntity> visibleModules;
            if (isAdmin || isPlatformAdmin) {
                visibleModules = modules;
            } else {
                // Construir set de module_ids alcanzables por los permisos del usuario.
                // Los authorities tienen formato 'PERM_<CODE>'; cruzamos con permissions.module_id.
                java.util.Set<String> permCodesAuth = new java.util.HashSet<>();
                if (auth != null) {
                    for (var ga : auth.getAuthorities()) {
                        if (ga.getAuthority().startsWith("PERM_")) {
                            permCodesAuth.add(ga.getAuthority().substring(5));
                        }
                    }
                }
                // QA Bloque AT (HU-PA-13 E7, 2026-05-13): sumar permisos
                // temporales ACTIVE en la ventana actual al set. Sin esto, un
                // OPERADOR_NOMINA con permiso temporal puntual (ej.
                // PAR.PERMISOS_TEMPORALES.VER) NO ve el modulo Parametrizacion
                // en el dashboard porque su rol no tiene permisos PAR.*. El
                // JWT solo trae authorities del rol — los temporales viven en
                // BD y deben resolverse aqui en cada request.
                if (auth != null && auth.getName() != null) {
                    try {
                        Optional<User> userOpt = userRepository.findByEmail(auth.getName());
                        if (userOpt.isPresent()) {
                            java.util.Set<String> temp = temporaryPermissionService
                                    .computeEffectiveCodes(userOpt.get().getId());
                            if (temp != null) permCodesAuth.addAll(temp);
                        }
                    } catch (RuntimeException ex) {
                        // defensivo: si falla calculo temporal, NO bloqueamos
                        // el resto del menu (sigue con permisos del rol).
                    }
                }
                java.util.Set<Long> accessibleModuleIds = permCodesAuth.isEmpty()
                        ? java.util.Collections.emptySet()
                        : moduleRepository.findModuleIdsByPermissionCodes(permCodesAuth);
                visibleModules = modules.stream()
                        .filter(m -> accessibleModuleIds.contains(m.getId()))
                        .toList();
            }

            // Bloque AM (2026-05-03): aplicar orden persistido en companies.module_order
            // si el tenant lo configuro via /parametrizacion/navegacion (HU-PA-NAV-01).
            // Si no hay orden persistido, los modulos quedan en su orden natural por DB.
            // Modulos que no esten en el orden persistido (ej. nuevos) se agregan al final.
            final List<ModuleEntity> orderedModules = applyTenantModuleOrder(visibleModules);

            List<ModuleDTO> moduleDTOs = orderedModules.stream()
                    .map(module -> ModuleDTO.builder()
                            .id(module.getId())
                            .name(module.getName())
                            .description(module.getDescription())
                            .url(module.getUrl())
                            .icon(module.getIcon())
                            .position(module.getPosition())
                            .status(module.getStatus())
                            .menus(menuService.getMenusByModuleId(module.getId()))
                            .build())
                    .toList();

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Módulos obtenidos correctamente"), Optional.of(moduleDTOs)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    public ResponseEntity<?> storeModule(
            @Valid @RequestBody ModuleDTO request,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        try {
            if (moduleRepository.existsByNameAndDeletedAtIsNull(request.getName())) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El nombre del módulo ya existe"))
                );
            }
            if (moduleRepository.existsByUrlAndDeletedAtIsNull(request.getUrl())) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("La url del módulo ya existe"))
                );
            }
            moduleRepository.save(ModuleEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .url(request.getUrl())
                .icon(request.getIcon())
                .position(request.getPosition())
                .status(request.getStatus())
                .build());
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Módulo creado correctamente"), Optional.empty()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al guardar el módulo"))
            );
        }
    }

    public ResponseEntity<?> updateModule(
            @Valid @RequestBody ModuleDTO request,
            BindingResult bindingResult) {
        try {
            if (moduleRepository.existsByNameAndIdNot(request.getName(), request.getId())) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El nombre del módulo ya existe"))
                );
            }

            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondJson(bindingResult)
                );
            }

            ModuleEntity module = moduleRepository.findById(request.getId())
                    .orElseThrow(() -> new RuntimeException("Módulo no encontrado"));

            module.setName(request.getName());
            module.setDescription(request.getDescription());
            module.setUrl(request.getUrl());
            module.setIcon(request.getIcon());
            module.setPosition(request.getPosition());
            module.setStatus(request.getStatus());
            module.setUpdatedAt(LocalDateTime.now());
            module = moduleRepository.save(module);
            auditPublisher.publishUpdate(AuditModule.PA, "Module", module.getId(), "Module actualizado id=" + module.getId());
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Módulo actualizado correctamente"), Optional.empty()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
            );
        }
    }

    /**
     * PA-RF-17: Elimina un modulo de forma logica (soft delete).
     *
     * <p>Regla de negocio: no se puede eliminar un modulo que todavia tiene
     * menus activos asociados. Esto preserva la integridad referencial y
     * evita dejar menus huerfanos en la BD.
     *
     * @param id ID del modulo a eliminar
     * @return 200 si la eliminacion fue exitosa; 400 si tiene menus activos o no existe
     */
    public ResponseEntity<?> deleteModule(Long id) {
        try {
            ModuleEntity module = moduleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Módulo no encontrado"));

            // PA-RF-17: validar que el modulo no tenga menus activos antes de eliminarlo
            if (menuRepository.existsByModule_IdAndDeletedAtIsNull(id)) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                        Optional.of("No se puede eliminar el módulo porque tiene menús activos asociados"))
                );
            }

            module.setDeletedAt(LocalDateTime.now());
            module = moduleRepository.save(module);
            auditPublisher.publishDelete(AuditModule.PA, "Module", module.getId(), "Module eliminado id=" + module.getId());
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Módulo eliminado correctamente"), Optional.empty()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
            );
        }
    }

    /**
     * Retorna todas las rutas de menú del sistema (sin filtrar por permisos).
     * Se usa en el frontend para distinguir 403 (sin permisos) de 404 (no existe).
     */
    public ResponseEntity<?> getAllMenuPaths() {
        try {
            List<ModuleEntity> modules = moduleRepository.findActiveModulesWithActiveMenus(
                    parseStatus(ModelStatus.ACTIVE), parseStatus(MenuStatus.ACTIVE));
            List<String> paths = new ArrayList<>();
            for (ModuleEntity module : modules) {
                List<com.sigcon.backend.parametrization.menu.Menu> menus = menuService.getMenusByModuleId(module.getId());
                collectMenuPaths(menus, module.getUrl(), paths);
            }
            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(Optional.of("Rutas obtenidas"), Optional.of(paths)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    private void collectMenuPaths(List<com.sigcon.backend.parametrization.menu.Menu> menus, String parentPath, List<String> paths) {
        if (menus == null) return;
        for (com.sigcon.backend.parametrization.menu.Menu menu : menus) {
            String menuPath = menu.getPath() != null ? menu.getPath() : "";
            String fullPath = "/" + (parentPath != null ? parentPath : "");
            if (!menuPath.isEmpty()) fullPath = fullPath + "/" + menuPath;
            fullPath = fullPath.replaceAll("/+", "/");
            paths.add(fullPath);
            if (menu.getChildrens() != null && !menu.getChildrens().isEmpty()) {
                collectMenuPaths(menu.getChildrens(), fullPath.replaceFirst("^/", ""), paths);
            }
        }
    }

    // Utils

    private boolean noFilters(ModuleDataTableRequest request) {
        return isBlank(request.getName())
                && isBlank(request.getDescription())
                && isBlank(request.getUrl())
                && isBlank(request.getIcon())
                && request.getPosition() == null
                && request.getStatus() == null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ModelStatus parseStatus(String statusStr) {
        if (statusStr == null || statusStr.trim().isEmpty()) {
            return null;
        }
        try {
            return ModelStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String parseStatus(MenuStatus status) {
        return status.name();
    }

    private String parseStatus(ModelStatus status) {
        return status.name();
    }

    /**
     * Bloque AM (2026-05-03): aplica el orden persistido en
     * {@code companies.module_order} (HU-PA-NAV-01) a la lista de modulos
     * visibles del menu lateral. Lo hace por tenant: cada empresa configura
     * el orden de sus modulos.
     *
     * <p>Comportamiento:
     * <ul>
     *   <li>Si NO hay tenant activo (PLATFORM_ADMIN): retorna sin reordenar.</li>
     *   <li>Si la lista persistida esta vacia: retorna sin reordenar (default DB).</li>
     *   <li>Modulos no listados en el orden persistido (ej. agregados nuevos)
     *       quedan al final, en su orden natural.</li>
     * </ul>
     */
    private List<ModuleEntity> applyTenantModuleOrder(List<ModuleEntity> modules) {
        Long tenantId = TenantContext.getCompanyId();
        if (tenantId == null) {
            return modules; // platform admin u operacion sin tenant
        }
        try {
            List<Long> persistedOrder = navSettingsService.getOrder(tenantId);
            if (persistedOrder == null || persistedOrder.isEmpty()) {
                return modules;
            }
            Map<Long, ModuleEntity> byId = new HashMap<>();
            for (ModuleEntity m : modules) byId.put(m.getId(), m);
            List<ModuleEntity> ordered = new ArrayList<>();
            // 1) los que estan en el orden persistido, en ese orden
            for (Long id : persistedOrder) {
                ModuleEntity m = byId.remove(id);
                if (m != null) ordered.add(m);
            }
            // 2) los que no estaban en el orden persistido, en orden natural al final
            for (ModuleEntity m : modules) {
                if (byId.containsKey(m.getId())) ordered.add(m);
            }
            return ordered;
        } catch (Exception ex) {
            // si algo falla, no romper el menu: devolver orden original
            return modules;
        }
    }
}
