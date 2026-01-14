package com.sigcon.backend.application.menu.port.in;

import java.util.List;

import com.sigcon.backend.domain.menu.Menu;

public interface MenuUseCase {

    List<Menu> getActiveMenus();
}
