package com.sigcon.backend.parametrization.menu.port.out;

import java.util.List;
import java.util.Map;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;

public interface MenuRepositoryPort {

    List<Menu> findMenus();
    Map<Long, List<Menu>> findMenusByModuleId(Long moduleId);
    MenuEntity saveMenu(MenuEntity menuEntity);
}
