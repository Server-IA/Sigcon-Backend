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
    public Map<Long, List<Menu>> findMenusByModuleIdAndRoles(Long moduleId, List roles) {
        return repository.findMenusByModuleIdAndRoles(moduleId, roles, MenuStatus.ACTIVE).stream()
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
            .deletedAt(e.getDeleted_at())
            .createdAt(e.getCreated_at())
            .updatedAt(e.getUpdated_at())
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
            .deletedAt(m.getDeleted_at())
            .createdAt(m.getCreated_at())
            .updatedAt(m.getUpdated_at())
            .build());
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
            .updated_at(LocalDateTime.now())
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
            .updated_at(saved.getUpdated_at())
            .build();
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
            .parentId(e.getParentId())
            .moduleId(e.getModuleId())
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
            .parentId(e.getParentId())
            .moduleId(e.getModuleId())
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
        menu.setDeleted_at(LocalDateTime.now());
        return repository.save(menu);
    }
}