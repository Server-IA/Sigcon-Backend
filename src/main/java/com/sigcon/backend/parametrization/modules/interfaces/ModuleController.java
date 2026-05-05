package com.sigcon.backend.parametrization.modules.interfaces;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.sigcon.backend.parametrization.modules.application.ModuleDTO;
import com.sigcon.backend.parametrization.modules.domain.model.ModuleDataTableRequest;
import com.sigcon.backend.parametrization.modules.domain.model.ModuleEntity;
import com.sigcon.backend.parametrization.modules.domain.service.ModuleService;
import com.sigcon.backend.utils.DataTableRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/modules")
@RequiredArgsConstructor
@Tag(name = "1. Módulo de Parametrización - Módulos", description = "Endpoints para gestion de módulos")

public class ModuleController {

    private final ModuleService moduleService;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_MODULES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getModules(
            @RequestBody(required = false) DataTableRequest dtRequest) {
        return moduleService.getModulesPaged(dtRequest);
    }

    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_MODULES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> storeModule(@Valid @RequestBody ModuleDTO request, BindingResult bindingResult) {
        return moduleService.storeModule(request, bindingResult);
    }

    @PutMapping("/update")
    @PreAuthorize("hasAuthority('PERM_UPDATE_MODULES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> updateModule(@Valid @RequestBody ModuleDTO request, BindingResult bindingResult) {
        return moduleService.updateModule(request, bindingResult);
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_MODULES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> deleteModule(@PathVariable Long id) {
        return moduleService.deleteModule(id);
    }

    /**
     * Devuelve los modulos y menus que el usuario autenticado puede ver.
     * Accesible a cualquier usuario logueado: el filtrado por rol se hace
     * dentro del service (via menu_permissions). Si un menu no tiene
     * permisos configurados, queda visible para todos (compat hacia atras).
     */
    @GetMapping("/menu")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getModulesMenu() {
        return moduleService.getModulesMenu();
    }

    /** Retorna todas las rutas del sistema para distinguir 403 vs 404 en el frontend */
    @GetMapping("/menu/all-paths")
    public ResponseEntity<?> getAllMenuPaths() {
        return moduleService.getAllMenuPaths();
    }

}
