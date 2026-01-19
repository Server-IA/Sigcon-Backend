package com.sigcon.backend.parametrization.menu.port.out;

import java.util.List;

import com.sigcon.backend.parametrization.menu.Menu;

public interface MenuRepositoryPort {

    List<Menu> findActiveMenus();
}
