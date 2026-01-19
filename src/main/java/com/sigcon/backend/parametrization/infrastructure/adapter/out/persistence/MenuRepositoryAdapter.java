package com.sigcon.backend.parametrization.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.stream.Collectors;

import com.sigcon.backend.parametrization.menu.port.out.MenuRepositoryPort;
import com.sigcon.backend.parametrization.menu.Menu;

public class MenuRepositoryAdapter implements MenuRepositoryPort {

    private final SpringDataMenuRepository repository;

    public MenuRepositoryAdapter(SpringDataMenuRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Menu> findActiveMenus() {
        return repository.findByActiveTrueOrderByMenuOrderAsc().stream()
            .map(e -> new Menu(
                e.getId(),
                e.getLabel(),
                e.getIcon(),
                e.getPath(),
                e.getMenuOrder(),
                e.getParentId(),
                e.getActive()
            ))
            .collect(Collectors.toList());
    }

}
