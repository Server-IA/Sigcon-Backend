package com.sigcon.backend.parametrization.modules.domain.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestBody;

import com.sigcon.backend.parametrization.modules.application.ModuleDTO;
import com.sigcon.backend.parametrization.modules.domain.model.Module;
import com.sigcon.backend.parametrization.modules.domain.model.enums.Status;
import com.sigcon.backend.parametrization.modules.domain.repository.ModuleRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ModuleRepository moduleRepository;

    public ResponseEntity<?> getModulesPaged(ModuleDTO request, Pageable pageable) {
        try {

            Page<Module> modules;

            if (request == null || noFilters(request)) {
                modules = moduleRepository.findAll(pageable);
            } else {
                modules = moduleRepository.searchModules(
                    request.getName(),
                    request.getDescription(),
                    request.getUrl(),
                    request.getIcon(),
                    request.getPosition(),
                    request.getStatus(),
                    pageable
                );
            }

            Page<ModuleDTO> response = modules.map(module -> {
                return ModuleDTO.builder()
                    .id(module.getId())
                    .name(module.getName())
                    .description(module.getDescription())
                    .url(module.getUrl())
                    .icon(module.getIcon())
                    .position(module.getPosition())
                    .status(module.getStatus())
                    .build();
            });

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al obtener los módulos");
        }
    }

    public ResponseEntity<?> getModules(ModuleDTO request) {
        try {
            List<Module> modules = moduleRepository.findAllByStatus(Status.ACTIVE);
            return ResponseEntity.ok(modules);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al obtener los módulos");
        }
    }

    public ResponseEntity<?> storeModule(
        @Valid @RequestBody Module request,
        BindingResult bindingResult) {
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
            Module module = moduleRepository.save(request);
            return ResponseEntity.ok(module);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al guardar el módulo");
        }
    }
    
    private boolean noFilters(ModuleDTO request) {
        return isBlank(request.getName())
                && isBlank(request.getDescription())
                && isBlank(request.getUrl())
                && isBlank(request.getIcon())
                && request.getPosition() == null
                && request.getStatus() == null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
