package com.sigcon.backend.parametrization.modules.interfaces;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

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

public class ModuleController {

    private final ModuleService moduleService;

    @PostMapping
    public ResponseEntity<?> getModules(
        @RequestBody(required = false) DataTableRequest dtRequest
    ) {
        return moduleService.getModulesPaged(dtRequest);
    }

    @PostMapping("/store")
    public ResponseEntity<?> storeModule(@Valid @RequestBody ModuleDTO request, BindingResult bindingResult) {
        return moduleService.storeModule(request, bindingResult);
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateModule(@Valid @RequestBody ModuleDTO request, BindingResult bindingResult) {
        return moduleService.updateModule(request, bindingResult); 
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteModule(@PathVariable Long id) {
        return moduleService.deleteModule(id);
    }

    @GetMapping("/menu")
    public ResponseEntity<?> getModulesMenu() {
        return moduleService.getModulesMenu();
    }

}
