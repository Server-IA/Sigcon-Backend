package com.sigcon.backend.parametrization.modules.domain.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestBody;

import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;
import com.sigcon.backend.parametrization.menu.service.MenuService;
import com.sigcon.backend.parametrization.modules.application.ModuleDTO;
import com.sigcon.backend.parametrization.modules.domain.model.ModuleDataTableRequest;
import com.sigcon.backend.parametrization.modules.domain.model.DataTableResponse;
import com.sigcon.backend.parametrization.modules.domain.model.Module;
import com.sigcon.backend.parametrization.modules.domain.model.enums.ModelStatus;
import com.sigcon.backend.parametrization.modules.domain.repository.ModuleRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ModuleRepository moduleRepository;
    private final MenuService menuService;

    public ResponseEntity<?> getModulesPaged(ModuleDataTableRequest request) {
        try {
    
            int start  = Math.max(0, request.getStart());
            int length = request.getLength();
    
            Page<Module> modules;
    
            // 🔥 CASO: traer TODOS los registros
            if (length == -1) {
    
                List<Module> all = noFilters(request)
                    ? moduleRepository.findAll()
                    : moduleRepository.searchModules(
                        request.getName(),
                        request.getDescription(),
                        request.getUrl(),
                        request.getIcon(),
                        request.getPosition(),
                        parseStatus(request.getStatus()),
                        Pageable.unpaged()
                    ).getContent();
    
                return ResponseEntity.ok(
                    DataTableResponse.from(all, request.getDraw())
                );
            }
    
            // 🔹 paginación normal
            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;
            Pageable pageable = PageRequest.of(page, safeLength);
    
            modules = noFilters(request)
                ? moduleRepository.findAll(pageable)
                : moduleRepository.searchModules(
                    request.getName(),
                    request.getDescription(),
                    request.getUrl(),
                    request.getIcon(),
                    request.getPosition(),
                    parseStatus(request.getStatus()),
                    pageable
                );
    
            return ResponseEntity.ok(
                DataTableResponse.from(modules, request.getDraw())
            );
    
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al obtener los módulos");
        }
    }
    

    public ResponseEntity<?> getModules(ModuleDTO request) {
        try {
            List<Module> modules = moduleRepository.findAllByStatus(ModelStatus.ACTIVE);
            return ResponseEntity.ok(modules);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al obtener los módulos");
        }
    }

    public ResponseEntity<?> getModulesMenu() {
        try {
            List<Module> modules = moduleRepository.findActiveModulesWithActiveMenus(ModelStatus.ACTIVE, MenuStatus.ACTIVE);
            List<ModuleDTO> moduleDTOs = modules.stream()
                .map(module -> ModuleDTO.builder()
                    .id(module.getId())
                    .name(module.getName())
                    .description(module.getDescription())
                    .url(module.getUrl())
                    .icon(module.getIcon())
                    .position(module.getPosition())
                    .status(module.getStatus())
                    .menus(
                        menuService.getMenusByModuleId(module.getId())
                    )
                    .build())
                .toList();

            return ResponseEntity.ok(moduleDTOs);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al obtener los módulos del menú");
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
    
    private boolean noFilters(ModuleDataTableRequest request) {
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

    private ModelStatus parseStatus(String statusStr) {
        if (statusStr == null || statusStr.trim().isEmpty()) {
            return null;
        }
        try {
            return ModelStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
