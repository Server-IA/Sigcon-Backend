package com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.sigcon.backend.parametrization.menu.port.out.MenuRepositoryPort;
import com.sigcon.backend.parametrization.modules.application.ModuleDTO;
import com.sigcon.backend.parametrization.modules.domain.model.ModuleEntity;
import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

public class MenuRepositoryAdapter implements MenuRepositoryPort {

    private final SpringDataMenuRepository repository;

    public MenuRepositoryAdapter(SpringDataMenuRepository repository) {
        this.repository = repository;
    }
    
    @Override
    public Page<MenuEntity> findAll(Specification<MenuEntity> spec, Pageable pageable) {
        return repository.findAll(spec, pageable);
    }

    @Override
    public Map<Long, List<Menu>> findMenusByModuleIdAndRoles(Long moduleId, List<Long> roles, boolean isAdmin) {
        return repository.findMenusByModuleIdAndRoles(moduleId, roles, parseStatus(MenuStatus.ACTIVE), isAdmin).stream()
            .map(this::entityToMenu)
            .collect(Collectors.groupingBy(menu -> menu.getModuleId() != null ? menu.getModuleId() : 0L));
    }

    @Override
    public Map<Long, List<Menu>> findMenusByParentId(Long parentId) {
        return repository.findMenusByParentId(parentId, parseStatus(MenuStatus.ACTIVE)).stream()
            .map(this::entityToMenu)
            .collect(Collectors.groupingBy(menu -> menu.getParentId() != null ? menu.getParentId() : 0L));
    }

    private Menu entityToMenu(MenuEntity e) {
        return Menu.builder()
            .id(e.getId())
            .label(e.getLabel())
            .icon(e.getIcon())
            .path(e.getPath())
            .menuOrder(e.getMenuOrder())
            .parentId(e.getParent() != null ? e.getParent().getId() : null)
            .parent(
                null != e.getParent() ?
                    Menu.builder()
                        .id(e.getParent().getId())
                        .label(e.getParent().getLabel())
                        .icon(e.getParent().getIcon())
                        .build()
                    : null)
            .moduleId(e.getModule() != null ? e.getModule().getId() : null)
            .module(
                null != e.getModule() ?
                    ModuleDTO.builder()
                    .id(e.getModule().getId())
                    .name(e.getModule().getName())
                    .build()
                    : null)
            .status(e.getStatus())
            .component(e.getComponent())
            .createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt())
            .deletedAt(e.getDeletedAt())
            .build();
    }

    @Override
    public MenuEntity saveMenu(MenuEntity menu) {
        MenuEntity saved = repository.save(menu);
        
        return saved;
    }

    @Override
    public Optional<MenuEntity> findMenuByLabel(String label) {
        return repository.findByLabel(label).map(e -> MenuEntity.builder()
            .id(e.getId())
            .label(e.getLabel())
            .icon(e.getIcon())
            .path(e.getPath())
            .menuOrder(e.getMenuOrder())
            .parent(e.getParent())
            .module(e.getModule())
            .status(e.getStatus())
            .component(e.getComponent())
            .build()); 
    }

    @Override
    public Optional<Menu> findMenuById(Long id) {
        return repository.findById(id).map(m -> Menu.builder()
            .id(m.getId())
            .label(m.getLabel())
            .icon(m.getIcon())
            .path(m.getPath())
            .menuOrder(m.getMenuOrder())
            .parent(
                null != m.getParent() ?
                    Menu.builder()
                        .id(m.getParent().getId())
                        .label(m.getParent().getLabel())
                        .icon(m.getParent().getIcon())
                        .build()
                    : null)
            .module(ModuleDTO.builder()
                .id(m.getModule().getId())
                .name(m.getModule().getName())
                .build())
            .status(m.getStatus())
            .component(m.getComponent())
            .createdAt(m.getCreatedAt())
            .updatedAt(m.getUpdatedAt())
            .deletedAt(m.getDeletedAt())
            .build());
    }

    @Override
    public MenuEntity updateMenu(MenuEntity menuEntity) {
        MenuEntity saved = repository.save(menuEntity);

        return saved;
    }

    @Override
    public MenuEntity findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    @Override
    public Optional<MenuEntity> findMenuByPath(String path) {
        return repository.findMenusByPath(path).map(e -> MenuEntity.builder()
            .id(e.getId())
            .label(e.getLabel())
            .icon(e.getIcon())
            .path(e.getPath())
            .menuOrder(e.getMenuOrder())
            .parent(e.getParent())
            .module(e.getModule())
            .status(e.getStatus())
            .component(e.getComponent())
            .build());
    }

    @Override
    public Optional<MenuEntity> findMenuByComponent(String component) {
        return repository.findMenusByComponent(component).map(e -> MenuEntity.builder()
            .id(e.getId())
            .label(e.getLabel())
            .icon(e.getIcon())
            .path(e.getPath())
            .menuOrder(e.getMenuOrder())
            .parent(e.getParent())
            .module(e.getModule())
            .status(e.getStatus())
            .component(e.getComponent())
            .build());
    }

    @Override
    public MenuEntity deleteMenu(Long id) {
        MenuEntity menu = findById(id);
        if(menu == null) {
            throw new RuntimeException("Menú no encontrado");
        }
        menu.setDeletedAt(LocalDateTime.now());
        return repository.save(menu);
    }

    private String parseStatus(MenuStatus status) {
        return status.name();
    }
}