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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ModuleRepository moduleRepository;
    private final MenuService menuService;
    private final SpringDataMenuRepository menuRepository;
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

            final List<ModuleEntity> visibleModules;
            if (isAdmin) {
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
                java.util.Set<Long> accessibleModuleIds = permCodesAuth.isEmpty()
                        ? java.util.Collections.emptySet()
                        : moduleRepository.findModuleIdsByPermissionCodes(permCodesAuth);
                visibleModules = modules.stream()
                        .filter(m -> accessibleModuleIds.contains(m.getId()))
                        .toList();
            }

            List<ModuleDTO> moduleDTOs = visibleModules.stream()
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
}
