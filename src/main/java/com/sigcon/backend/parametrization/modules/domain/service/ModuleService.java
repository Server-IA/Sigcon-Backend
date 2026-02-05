package com.sigcon.backend.parametrization.modules.domain.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
import com.sigcon.backend.parametrization.modules.domain.model.Module;
import com.sigcon.backend.parametrization.modules.domain.model.enums.ModelStatus;
import com.sigcon.backend.parametrization.modules.domain.repository.ModuleRepository;
import com.sigcon.backend.utils.DataTableResponse;

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
                    ? moduleRepository.findAllAndDeletedAtIsNull(Pageable.unpaged()).getContent()
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
                ? moduleRepository.findAllAndDeletedAtIsNull(pageable)
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

            Map<String, Object> response = new HashMap<>();
            response.put("title", "Error de validación");
            response.put("errors", errors);
    
            return ResponseEntity.badRequest().body(response);
        }

        try {
            if (moduleRepository.existsByName(request.getName())) {

                Map<String, String> fieldError = new HashMap<>();
                fieldError.put("field", "name");
                fieldError.put("message", "El nombre del módulo ya existe");
            
                List<Map<String, String>> errors = new ArrayList<>();
                errors.add(fieldError);
            
                Map<String, Object> response = new HashMap<>();
                response.put("title", "Error de validación");
                response.put("errors", errors);
            
                return ResponseEntity.badRequest().body(response);
            }
            Module module = moduleRepository.save(request);
            return ResponseEntity.ok(module);
        } catch (Exception e) {

            Map<String, Object> response = new HashMap<>();
            response.put("title", "Error interno");
            response.put("message", "Error al guardar el módulo");

            return ResponseEntity.badRequest().body(response);
        }
    }

    public ResponseEntity<?> updateModule(
        @Valid @RequestBody Module request,
        BindingResult bindingResult){
        try {
            if (moduleRepository.existsByNameAndIdNot(request.getName(), request.getId())) {
                Map<String, String> fieldError = new HashMap<>();
                fieldError.put("field", "name");
                fieldError.put("message", "El nombre del módulo ya existe");

                List<Map<String, String>> errors = new ArrayList<>();
                errors.add(fieldError);

                Map<String, Object> response = new HashMap<>();
                response.put("title", "Error de validación");
                response.put("errors", errors);

                return ResponseEntity.badRequest().body(response);
            }

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
    
                Map<String, Object> response = new HashMap<>();
                response.put("title", "Error de validación");
                response.put("errors", errors);
        
                return ResponseEntity.badRequest().body(response);
            }

            Module module = moduleRepository.findById(request.getId())
                .orElseThrow(() -> new RuntimeException("Módulo no encontrado"));

            module.setName(request.getName());
            module.setDescription(request.getDescription());   
            module.setUrl(request.getUrl());
            module.setIcon(request.getIcon());
            module.setPosition(request.getPosition());   
            module.setStatus(request.getStatus());
            // module.setUpdatedAt(LocalDateTime.now());
            module = moduleRepository.save(module);
            return ResponseEntity.ok(module);
        }catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("title", "Error interno");
            response.put("message", "Error al guardar el módulo");

            return ResponseEntity.badRequest().body(response);
        }
    }

    public ResponseEntity<?> deleteModule(Long id) {
        try {
            Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Módulo no encontrado"));
            module.setDeleted_at(LocalDateTime.now());
            module = moduleRepository.save(module);
            return ResponseEntity.ok(module);
        }
        catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("title", "Error interno");
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    // Utils
    
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
