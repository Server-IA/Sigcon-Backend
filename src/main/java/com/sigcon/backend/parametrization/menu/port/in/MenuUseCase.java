package com.sigcon.backend.parametrization.menu.port.in;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;

public interface MenuUseCase {

    List<Menu> getMenus();
    List<Menu> getMenusByModuleId(Long moduleId);
    ResponseEntity<?> saveMenu(MenuEntity menuEntity, BindingResult bindingResult);
}
