package com.sigcon.backend.parametrization.menu.port.in;

import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuDataTableRequest;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;

public interface MenuUseCase {

    ResponseEntity<?> getMenusDataTable(MenuDataTableRequest request);
    List<Menu> getMenusByModuleId(Long moduleId);
    ResponseEntity<?> saveMenu(MenuEntity menuEntity, BindingResult bindingResult);
    Optional<MenuEntity> findMenuByLabel(String label);
    boolean noFilters(MenuDataTableRequest request);
    Optional<Menu> findMenuById(Long id);

    ResponseEntity<?> updateMenu(MenuEntity menuEntity, BindingResult bindingResult);
}
