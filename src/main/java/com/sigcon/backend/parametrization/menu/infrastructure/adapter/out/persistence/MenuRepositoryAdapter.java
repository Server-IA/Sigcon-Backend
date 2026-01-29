package com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.sigcon.backend.parametrization.menu.port.out.MenuRepositoryPort;
import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

public class MenuRepositoryAdapter implements MenuRepositoryPort {

    private final SpringDataMenuRepository repository;

    public MenuRepositoryAdapter(SpringDataMenuRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Menu> findMenus() {
        return repository.findByOrderByMenuOrderAsc().stream()
            .map(e -> Menu.builder()
                .id(e.getId())
                .label(e.getLabel())
                .icon(e.getIcon())    
                .path(e.getPath())
                .menuOrder(e.getMenuOrder())
                .parentId(e.getParentId())
                .moduleId(e.getModuleId())
                .status(MenuStatus.ACTIVE)
                .component(e.getComponent())
                .build()
            ).collect(Collectors.toList());
    }

    @Override
    public Map<Long, List<Menu>> findMenusByModuleId(Long moduleId) {
        return repository.findByModuleIdAndStatusOrderByMenuOrderAsc(moduleId, MenuStatus.ACTIVE).stream()
            .map(e -> Menu.builder()
                .id(e.getId())
                .label(e.getLabel())
                .icon(e.getIcon())
                .path(e.getPath())
                .menuOrder(e.getMenuOrder())
                .parentId(e.getParentId())
                .moduleId(e.getModuleId())
                .status(MenuStatus.ACTIVE)
                .component(e.getComponent())
                .build()
            )
            .collect(Collectors.groupingBy(Menu::getModuleId));
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

}
