package com.sigcon.backend.parametrization.menu.port.in;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;
import com.sigcon.backend.utils.DataTableRequest;

public interface MenuUseCase {

    ResponseEntity<?> getMenusDataTable(DataTableRequest request);
    List<Menu> getMenusByModuleId(Long moduleId);
    ResponseEntity<?> saveMenu(MenuEntity menuEntity, BindingResult bindingResult);
    Optional<MenuEntity> findMenuByLabel(String label);
    
    Optional<Menu> findMenuById(Long id);

    ResponseEntity<?> updateMenu(MenuEntity menuEntity, BindingResult bindingResult);
    ResponseEntity<?> deleteMenu(Long id);

    Page<MenuEntity> findAll(Specification<MenuEntity> spec, Pageable pageable);
}
