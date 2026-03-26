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
@Tag(name = "Módulo de Parametrización")

public class ModuleController {

    private final ModuleService moduleService;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_MODULES') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> getModules(
            @RequestBody(required = false) DataTableRequest dtRequest) {
        return moduleService.getModulesPaged(dtRequest);
    }

    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_MODULES') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> storeModule(@Valid @RequestBody ModuleDTO request, BindingResult bindingResult) {
        return moduleService.storeModule(request, bindingResult);
    }

    @PutMapping("/update")
    @PreAuthorize("hasAuthority('PERM_UPDATE_MODULES') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> updateModule(@Valid @RequestBody ModuleDTO request, BindingResult bindingResult) {
        return moduleService.updateModule(request, bindingResult);
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_MODULES') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> deleteModule(@PathVariable Long id) {
        return moduleService.deleteModule(id);
    }

    @GetMapping("/menu")
    @PreAuthorize("hasAuthority('PERM_VIEW_MODULES_MENU') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> getModulesMenu() {
        return moduleService.getModulesMenu();
    }

}
