package com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.sigcon.backend.parametrization.menu.port.out.MenuRepositoryPort;
import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

public class MenuRepositoryAdapter implements MenuRepositoryPort {

    private final SpringDataMenuRepository repository;

    public MenuRepositoryAdapter(SpringDataMenuRepository repository) {
        this.repository = repository;
    }

    @Override
    public Page<Menu> findMenusAllAndDeletedAtIsNull(Pageable pageable) {
        return repository.findMenusAllAndDeletedAtIsNull(pageable)
            .map(this::entityToMenu);
    }

    @Override
    public Page<Menu> findMenusAllFiltersAndDeletedAtIsNull(
        String label,
        String description,
        String url,
        String icon,
        Integer position,
        MenuStatus menuStatus,
        Long moduleId,
        Long parentId,
        Pageable pageable) {
        return repository.findMenusAllFiltersAndDeletedAtIsNull(label, description, url, icon, position, menuStatus, moduleId, parentId, pageable)
            .map(this::entityToMenu);
    }

    @Override
    public Map<Long, List<Menu>> findMenusByModuleId(Long moduleId) {
        return repository.findMenusByModule(moduleId, MenuStatus.ACTIVE).stream()
            .map(this::entityToMenu)
            .collect(Collectors.groupingBy(Menu::getModuleId));
    }

    @Override
    public Map<Long, List<Menu>> findMenusByParentId(Long parentId) {
        return repository.findMenusByParentId(parentId, MenuStatus.ACTIVE).stream()
            .map(this::entityToMenu)
            .collect(Collectors.groupingBy(Menu::getParentId));
    }

    private Menu entityToMenu(MenuEntity e) {
        return Menu.builder()
            .id(e.getId())
            .label(e.getLabel())
            .icon(e.getIcon())
            .path(e.getPath())
            .menuOrder(e.getMenuOrder())
            .parentId(e.getParentId())
            .moduleId(e.getModuleId())
            .status(e.getStatus())
            .component(e.getComponent())
            .deletedAt(e.getDeletedAt())
            .createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt())
            .build();
    }

    @Override
    public MenuEntity saveMenu(MenuEntity menuEntity) {
        MenuEntity saved = repository.save(
            MenuEntity.builder()
                .id(menuEntity.getId())
                .label(menuEntity.getLabel())
                .icon(menuEntity.getIcon())
                .path(menuEntity.getPath())
                .menuOrder(menuEntity.getMenuOrder())
                .parentId(menuEntity.getParentId())
                .moduleId(menuEntity.getModuleId())
                .status(menuEntity.getStatus())
                .component(menuEntity.getComponent())
                .build()

        );
        
        return MenuEntity.builder()
            .id(saved.getId())
            .label(saved.getLabel())
            .icon(saved.getIcon())
            .path(saved.getPath())
            .menuOrder(saved.getMenuOrder())
            .parentId(saved.getParentId())
            .moduleId(saved.getModuleId())
            .status(saved.getStatus())
            .component(saved.getComponent())
            .build();
    }

    @Override
    public Optional<MenuEntity> findMenuByLabel(String label) {
        return repository.findByLabel(label).map(e -> MenuEntity.builder()
            .id(e.getId())
            .label(e.getLabel())
            .icon(e.getIcon())
            .path(e.getPath())
            .menuOrder(e.getMenuOrder())
            .parentId(e.getParentId())
            .moduleId(e.getModuleId())
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
            .parentId(m.getParentId())
            .moduleId(m.getModuleId())
            .status(m.getStatus())
            .component(m.getComponent())
            .deletedAt(m.getDeletedAt())
            .createdAt(m.getCreatedAt())
            .updatedAt(m.getUpdatedAt())
            .build());
    }

    @Override
    public boolean noFilters(MenuDataTableRequest request) {
        return request.getLabel() == null && request.getDescription() == null && request.getUrl() == null && request.getIcon() == null && request.getPosition() == null && request.getStatus() == null;
    }

    @Override
    public MenuEntity updateMenu(MenuEntity menuEntity) {
        MenuEntity saved = repository.save(MenuEntity.builder()
            .id(menuEntity.getId())
            .label(menuEntity.getLabel())
            .icon(menuEntity.getIcon())
            .path(menuEntity.getPath())
            .menuOrder(menuEntity.getMenuOrder())
            .parentId(menuEntity.getParentId())
            .moduleId(menuEntity.getModuleId())
            .status(menuEntity.getStatus())
            .component(menuEntity.getComponent())
            .updatedAt(LocalDateTime.now())
            .build());

        return MenuEntity.builder()
            .id(saved.getId())
            .label(saved.getLabel())
            .icon(saved.getIcon())
            .path(saved.getPath())
            .menuOrder(saved.getMenuOrder())
            .parentId(saved.getParentId())
            .moduleId(saved.getModuleId())
            .status(saved.getStatus())
            .component(saved.getComponent())
            .updatedAt(saved.getUpdatedAt())
            .build();
    }

    @Override
    public MenuEntity findById(Long id) {
        return repository.findById(id).orElse(null);
    }
}