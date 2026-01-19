package com.sigcon.backend.parametrization.menu.port.in;

import java.util.List;

import com.sigcon.backend.parametrization.menu.Menu;

public interface MenuUseCase {

    List<Menu> getActiveMenus();
}
