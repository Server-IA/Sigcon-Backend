package com.sigcon.backend.parametrization.menu.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestBody;

import com.sigcon.backend.parametrization.menu.port.in.MenuUseCase;
import com.sigcon.backend.parametrization.menu.port.out.MenuRepositoryPort;

import jakarta.validation.Valid;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;

public class MenuService implements MenuUseCase {
    private final MenuRepositoryPort menuRepositoryPort;

    public MenuService(MenuRepositoryPort menuRepositoryPort) {
        this.menuRepositoryPort = menuRepositoryPort;
    }

    @Override
    public List<Menu> getMenus() {
        return menuRepositoryPort.findMenus();
    }
    
    @Override
    public List<Menu> getMenusByModuleId(Long moduleId) {
        return menuRepositoryPort.findMenusByModuleId(moduleId).values().stream()
            .flatMap(List::stream)
            .toList();
    }

    @Override
    public ResponseEntity<?> saveMenu(
        @Valid @RequestBody MenuEntity menuEntity, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            List<Map<String, String>> errors = bindingResult.getFieldErrors()
                .stream()
                .map(error -> {
                    Map<String, String> err = new HashMap<>();
                    err.put("field", error.getField());
                    err.put("message", error.getDefaultMessage());
                    return err;
                })
                .toList();
            return ResponseEntity.badRequest().body(errors);
        }

        try {
            MenuEntity savedMenu = menuRepositoryPort.saveMenu(menuEntity);
            return ResponseEntity.ok(savedMenu);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al guardar el menú");
        }
    }
}
