package com.sigcon.backend.parametrization.menu.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestBody;

import com.sigcon.backend.parametrization.menu.port.in.MenuUseCase;
import com.sigcon.backend.parametrization.menu.port.out.MenuRepositoryPort;
import com.sigcon.backend.parametrization.modules.application.ModuleDTO;
import com.sigcon.backend.parametrization.modules.domain.model.ModuleEntity;
import com.sigcon.backend.parametrization.modules.domain.repository.ModuleRepository;
import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

@RequiredArgsConstructor

public class MenuService implements MenuUseCase {
    private final MenuRepositoryPort menuRepositoryPort;
    private final ModuleRepository moduleRepository;
    private final UserRepository userRepository;

    private final DataTableSpecificationBuilder<MenuEntity> menuSpecificationBuilder = new DataTableSpecificationBuilder<>();

    /**
     * Lista menus del sistema con paginacion y filtros DataTable.
     * Excluye menus eliminados logicamente e incluye datos del padre y modulo asociado.
     *
     * @param request parametros de paginacion, busqueda y orden del DataTable
     * @return ResponseEntity con DataTableResponse de menus
     */
    @Override
    public ResponseEntity<?> getMenusDataTable(DataTableRequest request) {

        try {

            int start = Math.max(0, request.getStart());
            int length = request.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                    ? Pageable.unpaged()
                    : PageRequest.of(page, safeLength);

            Specification<MenuEntity> spec = menuSpecificationBuilder.build(request)
                    .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

            Page<MenuEntity> menus = menuRepositoryPort.findAll(spec, pageable);

            Page<Menu> menusResponse = menus.map(menu -> Menu.builder()
                    .id(menu.getId())
                    .label(menu.getLabel())
                    .path(menu.getPath())
                    .icon(menu.getIcon())
                    .component(menu.getComponent())
                    .menuOrder(menu.getMenuOrder())
                    .status(menu.getStatus())
                    .parent(
                            null != menu.getParent() ? Menu.builder()
                                    .id(menu.getParent().getId())
                                    .label(menu.getParent().getLabel())
                                    .build()
                                    : null)
                    .module(
                            null != menu.getModule() ? ModuleDTO.builder()
                                    .id(menu.getModule().getId())
                                    .name(menu.getModule().getName())
                                    .build()
                                    : null)
                    .menuOrder(menu.getMenuOrder())
                    .status(menu.getStatus())
                    .visible(menu.getVisible())
                    .build());

            return ResponseEntity.ok(DataTableResponse.from(
                    menusResponse, request.getDraw()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Obtiene los menus de un modulo filtrados por los roles del usuario autenticado.
     * Los administradores (roleId=1) ven todos los menus del modulo.
     *
     * @param moduleId ID del modulo cuyos menus se consultan
     * @return lista de menus accesibles para el usuario actual
     */
    @Override
    public List<Menu> getMenusByModuleId(Long moduleId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Long> roleIds = user.getRoles().stream()
                .map(Role::getId)
                .collect(Collectors.toList());

        boolean isAdmin = roleIds.contains(1L);

        return menuRepositoryPort.findMenusByModuleIdAndRoles(moduleId, roleIds, isAdmin).values().stream()
                .flatMap(List::stream)
                .toList();
    }

    /**
     * Crea un nuevo menu en el sistema.
     * Valida unicidad de label, path y component. Verifica que el menu padre y modulo existan
     * y no esten eliminados logicamente.
     *
     * @param menu          datos del menu a crear
     * @param bindingResult resultado de validacion de campos obligatorios
     * @return ResponseEntity con mensaje de exito o errores de validacion/duplicidad
     */
    @Override
    public ResponseEntity<?> saveMenu(
            @Valid @RequestBody Menu menu, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        try {

            if (menu.getLabel() != null) {
                if (menuRepositoryPort.findMenuByLabel(menu.getLabel()).isPresent()) {
                    return ResponseEntity.badRequest()
                            .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("El nombre del menú ya existe")));
                }
            }

            if (menu.getPath() != null) {
                if (menuRepositoryPort.findMenuByPath(menu.getPath()).isPresent()) {
                    return ResponseEntity.badRequest()
                            .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("La URL ya existe")));
                }
            }

            if (menu.getComponent() != null) {
                if (menuRepositoryPort.findMenuByComponent(menu.getComponent()).isPresent()) {
                    return ResponseEntity.badRequest()
                            .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("El componente ya existe")));
                }
            }

            if (menu.getParentId() != null) {
                Menu parent = menuRepositoryPort.findMenuById(menu.getParentId())
                        .orElseThrow(() -> new RuntimeException("Menú padre no encontrado"));
                if (parent.getDeletedAt() != null) {
                    return ResponseEntity.badRequest().body(ErrorRespondJson
                            .getErrorRespondMessage(Optional.of("El menú padre ya se encuentra eliminado")));
                }
            }

            if (menu.getModuleId() != null) {
                ModuleEntity module = moduleRepository.findById(menu.getModuleId())
                        .orElseThrow(() -> new RuntimeException("Módulo no encontrado"));
                if (module.getDeletedAt() != null) {
                    return ResponseEntity.badRequest().body(ErrorRespondJson
                            .getErrorRespondMessage(Optional.of("El módulo ya se encuentra eliminado")));
                }
            }

            MenuEntity menuEntity = MenuEntity.builder()
                    .label(menu.getLabel())
                    .icon(menu.getIcon())
                    .path(menu.getPath())
                    .menuOrder(menu.getMenuOrder())
                    .parent(menu.getParentId() != null ? MenuEntity.builder()
                            .id(menu.getParentId())
                            .build() : null)
                    .module(ModuleEntity.builder()
                            .id(menu.getModuleId())
                            .build())
                    .status(menu.getStatus())
                    .visible(menu.getVisible())
                    .component(menu.getComponent())
                    .method(menu.getMethod())
                    .menuType(menu.getMenuType())
                    .deletedAt(menu.getDeletedAt())
                    .createdAt(menu.getCreatedAt())
                    .updatedAt(menu.getUpdatedAt())
                    .build();

            menuRepositoryPort.saveMenu(menuEntity);
            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(Optional.of("Menu creado correctamente"),
                            Optional.empty()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Actualiza un menu existente.
     * Valida unicidad de label, path y component excluyendo el registro actual.
     * Permite cambiar padre, modulo, visibilidad, orden, method y menuType.
     *
     * @param menu          datos actualizados del menu (debe incluir ID)
     * @param bindingResult resultado de validacion de campos obligatorios
     * @return ResponseEntity con mensaje de exito o errores de validacion
     */
    @Override
    public ResponseEntity<?> updateMenu(
            @Valid @RequestBody Menu menu, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try {

            MenuEntity menuEntity = menuRepositoryPort.findById(menu.getId());
            if (menuEntity == null) {
                return ResponseEntity.badRequest()
                        .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("Menú no encontrado")));
            }

            if (!menuEntity.getLabel().equals(menu.getLabel())) {
                if (menuRepositoryPort.findMenuByLabel(menu.getLabel()).isPresent()) {
                    return ResponseEntity.badRequest()
                            .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("El nombre del menú ya existe")));
                }
            }

            if (!menuEntity.getPath().equals(menu.getPath())) {
                if (menuRepositoryPort.findMenuByPath(menu.getPath()).isPresent()) {
                    return ResponseEntity.badRequest()
                            .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("La URL ya existe")));
                }
            }

            if (!menuEntity.getComponent().equals(menu.getComponent())) {
                if (menuRepositoryPort.findMenuByComponent(menu.getComponent()).isPresent()) {
                    return ResponseEntity.badRequest()
                            .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("El componente ya existe")));
                }
            }

            menuEntity.setLabel(menu.getLabel());
            menuEntity.setIcon(menu.getIcon());
            menuEntity.setPath(menu.getPath());
            menuEntity.setMenuOrder(menu.getMenuOrder());
            menuEntity.setVisible(menu.getVisible());
            menuEntity.setParent(
                    menu.getParentId() != null ? MenuEntity.builder()
                            .id(menu.getParentId())
                            .build() : null);

            menuEntity.setModule(
                    menu.getModuleId() != null ? ModuleEntity.builder()
                            .id(menu.getModuleId())
                            .build() : null);

            menuEntity.setStatus(menu.getStatus());
            menuEntity.setComponent(menu.getComponent());
            menuEntity.setMethod(menu.getMethod());
            menuEntity.setMenuType(menu.getMenuType());
            menuEntity.setUpdatedAt(LocalDateTime.now());

            menuRepositoryPort.updateMenu(menuEntity);
            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(Optional.of("Menú actualizado correctamente"),
                            Optional.empty()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Busca un menu por su etiqueta (label).
     *
     * @param label etiqueta del menu a buscar
     * @return Optional con la entidad MenuEntity si existe
     */
    @Override
    public Optional<MenuEntity> findMenuByLabel(String label) {
        return menuRepositoryPort.findMenuByLabel(label);
    }

    /**
     * Busca un menu por su ID.
     *
     * @param id ID del menu a buscar
     * @return Optional con el dominio Menu si existe, vacio si id es null
     */
    @Override
    public Optional<Menu> findMenuById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return menuRepositoryPort.findMenuById(id);
    }

    /**
     * Consulta menus con especificacion JPA y paginacion.
     *
     * @param spec     especificacion de filtros JPA
     * @param pageable parametros de paginacion
     * @return pagina de entidades MenuEntity que cumplen los filtros
     */
    @Override
    public Page<MenuEntity> findAll(Specification<MenuEntity> spec, Pageable pageable) {
        return menuRepositoryPort.findAll(spec, pageable);
    }

    private MenuStatus parseStatus(String statusStr) {
        if (statusStr == null || statusStr.trim().isEmpty()) {
            return null;
        }
        try {
            return MenuStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Elimina un menu de forma logica (soft delete).
     * La validacion de dependencias (submenus activos) se realiza en el adaptador del repositorio.
     *
     * @param id ID del menu a eliminar
     * @return ResponseEntity con mensaje de exito o error si tiene submenus activos
     */
    @Override
    public ResponseEntity<?> deleteMenu(Long id) {

        try {
            MenuEntity respond = menuRepositoryPort.deleteMenu(id);
            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(Optional.of("Menú eliminado correctamente"),
                            Optional.empty()));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}
