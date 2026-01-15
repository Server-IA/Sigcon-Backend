package com.sigcon.backend.infrastructure.adapter.in.web;

import java.util.List;


import org.springframework.web.bind.annotation.*;

import com.sigcon.backend.application.menu.port.in.MenuUseCase;
import com.sigcon.backend.domain.menu.Menu;

@RestController
@RequestMapping("/api/menus")

public class MenuController {

    private final MenuUseCase menuUseCase;

    public MenuController(MenuUseCase menuUseCase) {
        this.menuUseCase = menuUseCase;
    }

    @GetMapping
    public List<Menu> getMenus() {
        return menuUseCase.getActiveMenus();
    }

}
