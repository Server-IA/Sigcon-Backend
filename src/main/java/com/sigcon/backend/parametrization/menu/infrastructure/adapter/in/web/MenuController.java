package com.sigcon.backend.parametrization.menu.infrastructure.adapter.in.web;

import java.util.List;


import org.springframework.web.bind.annotation.*;

import com.sigcon.backend.parametrization.menu.port.in.MenuUseCase;

import jakarta.validation.Valid;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/api/menus")

public class MenuController {

    private final MenuUseCase menuUseCase;

    public MenuController(MenuUseCase menuUseCase) {
        this.menuUseCase = menuUseCase;
    }

    @GetMapping
    public List<Menu> getMenus() {
        return menuUseCase.getMenus();
    }

    @PostMapping("store")
    public ResponseEntity<?> storeMenu(@Valid @RequestBody MenuEntity menu, BindingResult bindingResult) {
        return menuUseCase.saveMenu(menu, bindingResult);
    }

}
