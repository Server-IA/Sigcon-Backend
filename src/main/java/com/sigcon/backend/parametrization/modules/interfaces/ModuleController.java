package com.sigcon.backend.parametrization.modules.interfaces;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import com.sigcon.backend.parametrization.modules.application.ModuleDTO;
import com.sigcon.backend.parametrization.modules.domain.model.ModuleDataTableRequest;
import com.sigcon.backend.parametrization.modules.domain.model.Module;
import com.sigcon.backend.parametrization.modules.domain.service.ModuleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/modules")
@RequiredArgsConstructor

public class ModuleController {

    private final ModuleService moduleService;

    @PostMapping
    public ResponseEntity<?> getModules(
        @RequestBody ModuleDataTableRequest dtRequest
    ) {
        return moduleService.getModulesPaged(dtRequest);
    }

    @PostMapping("/store")
    public ResponseEntity<?> storeModule(@Valid @RequestBody Module request, BindingResult bindingResult) {
        return moduleService.storeModule(request, bindingResult);
    }

    @GetMapping("/menu")
    public ResponseEntity<?> getModulesMenu() {
        return moduleService.getModulesMenu();
    }

}
