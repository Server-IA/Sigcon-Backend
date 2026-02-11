package com.sigcon.backend.parametrization.menu.service;

import java.lang.StackWalker.Option;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestBody;

import com.sigcon.backend.parametrization.menu.port.in.MenuUseCase;
import com.sigcon.backend.parametrization.menu.port.out.MenuRepositoryPort;
import com.sigcon.backend.parametrization.modules.domain.repository.ModuleRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;

import jakarta.validation.Valid;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuDataTableRequest;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

public class MenuService implements MenuUseCase {
    private final MenuRepositoryPort menuRepositoryPort;
    private final ModuleRepository moduleRepository;

    private final DataTableSpecificationBuilder<MenuEntity> menuSpecificationBuilder =
        new DataTableSpecificationBuilder<>();

    public MenuService(MenuRepositoryPort menuRepositoryPort, ModuleRepository moduleRepository) {
        this.menuRepositoryPort = menuRepositoryPort;
        this.moduleRepository = moduleRepository;
    }

    @Override
    public ResponseEntity<?> getMenusDataTable  (DataTableRequest request) {

        try{

            int start  = Math.max(0, request.getStart());
            int length = request.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<MenuEntity> spec = menuSpecificationBuilder.build(request)
                .and((root, query, cb) -> cb.isNull(root.get("deleted_at")));

            Page<MenuEntity> menus = menuRepositoryPort.findAll(spec, pageable);
    
            List<Menu> menusResponse = menus.getContent().stream()
                .map(menu -> Menu.builder()
                    .id(menu.getId())
                    .label(menu.getLabel())
                    .path(menu.getPath())
                    .icon(menu.getIcon())
                    .component(menu.getComponent())
                    .menuOrder(menu.getMenuOrder())
                    .status(menu.getStatus())
                    .parent(
                        menu.getParentId() == null ?
                            null : menuRepositoryPort.findMenuById(menu.getParentId())
                            .map(menuParent -> Menu.builder()
                                .id(menuParent.getId())
                                .label(menuParent.getLabel())
                                .build())
                            .orElse(null)
                    )
                    .module(
                        moduleRepository.findById(menu.getModuleId())
                        .orElse(null)
                    )   
                    .menuOrder(menu.getMenuOrder())
                    .status(menu.getStatus())
                    .build())
                .toList();
    
            return ResponseEntity.ok(DataTableResponse.from(menusResponse, request.getDraw()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @Override
    public List<Menu> getMenusByModuleId(Long moduleId) {
        return menuRepositoryPort.findMenusByModuleId(moduleId).values().stream()
            .flatMap(List::stream)
            .toList();
    }

    @Override
    public ResponseEntity<?> saveMenu(
        @Valid @RequestBody MenuEntity menuEntity, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        try {

            if(menuEntity.getLabel() != null) {
                if(menuRepositoryPort.findMenuByLabel(menuEntity.getLabel()).isPresent()) {
                    return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage("El nombre del menú ya existe"));
                }
            }

            if(menuEntity.getPath() != null) {
                if(menuRepositoryPort.findMenuByPath(menuEntity.getPath()).isPresent()) {
                    return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage("La URL ya existe"));
                }
            }

            if(menuEntity.getComponent() != null) {
                if(menuRepositoryPort.findMenuByComponent(menuEntity.getComponent()).isPresent()) {
                    return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage("El componente ya existe"));
                }
            }

            MenuEntity savedMenu = menuRepositoryPort.saveMenu(menuEntity);
            return ResponseEntity.ok(savedMenu);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(e.getMessage()));
        }
    }

    @Override
    public ResponseEntity<?> updateMenu(
        @Valid @RequestBody MenuEntity menuEntity, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try{

            Optional<Menu> menu = menuRepositoryPort.findMenuById(menuEntity.getId());
            if(menu == null) {  
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage("Menú no encontrado"));
            }

            if(!menu.get().getLabel().equals(menuEntity.getLabel())) {
                if(menuRepositoryPort.findMenuByLabel(menuEntity.getLabel()).isPresent()) {
                    return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage("El nombre del menú ya existe"));
                }
            }

            if(!menu.get().getPath().equals(menuEntity.getPath())) {
                if(menuRepositoryPort.findMenuByPath(menuEntity.getPath()).isPresent()) {
                    return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage("La URL ya existe"));
                }
            }
            
            if(!menu.get().getComponent().equals(menuEntity.getComponent())) {
                if(menuRepositoryPort.findMenuByComponent(menuEntity.getComponent()).isPresent()) {
                    return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage("El componente ya existe"));
                }
            }


            MenuEntity respond = menuRepositoryPort.updateMenu(menuEntity);
            return ResponseEntity.ok(respond);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(e.getMessage()));
        }
    }

    @Override
    public Optional<MenuEntity> findMenuByLabel(String label) {
        return menuRepositoryPort.findMenuByLabel(label);
    }

    @Override
    public Optional<Menu> findMenuById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return menuRepositoryPort.findMenuById(id);
    }

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

    @Override
    public ResponseEntity<?> deleteMenu(Long id) {

        try{
            MenuEntity respond = menuRepositoryPort.deleteMenu(id);
            return ResponseEntity.ok(respond);

        }
        catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(e.getMessage()));
        }
    }
}
