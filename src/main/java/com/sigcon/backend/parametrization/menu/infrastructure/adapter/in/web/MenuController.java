package com.sigcon.backend.parametrization.menu.infrastructure.adapter.in.web;

import java.util.List;


import org.springframework.web.bind.annotation.*;

import com.sigcon.backend.parametrization.menu.port.in.MenuUseCase;
import com.sigcon.backend.utils.DataTableRequest;

import jakarta.validation.Valid;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/menus")

public class MenuController {

    private final MenuUseCase menuUseCase;

    public MenuController(MenuUseCase menuUseCase) {
        this.menuUseCase = menuUseCase;
    }

    @PostMapping("/datatable")
    @PreAuthorize("hasAuthority('PERM_VIEW_MENUS')")
    public ResponseEntity<?> getMenusDataTable(
        @RequestBody(required = false) DataTableRequest request
    ) {
        return menuUseCase.getMenusDataTable(request);
    }


    @PostMapping("store")
    @PreAuthorize("hasAuthority('PERM_CREATE_MENUS')")
    public ResponseEntity<?> storeMenu(@Valid @RequestBody Menu menu, BindingResult bindingResult) {
        return menuUseCase.saveMenu(menu, bindingResult);
    }

    @PutMapping("/update")
    @PreAuthorize("hasAuthority('PERM_UPDATE_MENUS')")
    public ResponseEntity<?> updateMenu(@Valid @RequestBody Menu menu, BindingResult bindingResult) {
        return menuUseCase.updateMenu(menu, bindingResult);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_MENUS')")
    public ResponseEntity<?> deleteMenu(@PathVariable Long id) {
        return menuUseCase.deleteMenu(id);
    }

}
