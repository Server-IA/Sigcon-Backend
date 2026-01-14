package com.sigcon.backend.application.menu.service;

import java.util.List;

import com.sigcon.backend.application.menu.port.in.MenuUseCase;
import com.sigcon.backend.application.menu.port.out.MenuRepositoryPort;
import com.sigcon.backend.domain.menu.Menu;

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
