package com.sigcon.backend.application.menu.port.out;

import java.util.List;

import com.sigcon.backend.domain.menu.Menu;

public interface MenuRepositoryPort {

    List<Menu> findActiveMenus();
}
