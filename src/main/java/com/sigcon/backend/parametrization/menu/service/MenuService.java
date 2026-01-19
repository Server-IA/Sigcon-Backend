package com.sigcon.backend.parametrization.menu.service;

import java.util.List;

import com.sigcon.backend.parametrization.menu.port.in.MenuUseCase;
import com.sigcon.backend.parametrization.menu.port.out.MenuRepositoryPort;
import com.sigcon.backend.parametrization.menu.Menu;

public class MenuService implements MenuUseCase {
    private final MenuRepositoryPort menuRepositoryPort;

    public MenuService(MenuRepositoryPort menuRepositoryPort) {
        this.menuRepositoryPort = menuRepositoryPort;
    }

    @Override
    public List<Menu> getActiveMenus() {
        return menuRepositoryPort.findActiveMenus();
    }
}
