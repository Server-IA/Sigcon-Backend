package com.sigcon.backend.parametrization.modules.domain.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestBody;

import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;
import com.sigcon.backend.parametrization.menu.service.MenuService;
import com.sigcon.backend.parametrization.modules.application.ModuleDTO;
import com.sigcon.backend.parametrization.modules.domain.model.ModuleDataTableRequest;
import com.sigcon.backend.parametrization.modules.domain.model.ModuleEntity;
import com.sigcon.backend.parametrization.modules.domain.model.enums.ModelStatus;
import com.sigcon.backend.parametrization.modules.domain.repository.ModuleRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ModuleRepository moduleRepository;
    private final MenuService menuService;
    private final DataTableSpecificationBuilder<ModuleEntity> moduleSpecificationBuilder =
        new DataTableSpecificationBuilder<>();

    public ResponseEntity<?> getModulesPaged(DataTableRequest request) {
        try {

            int start = Math.max(0, request.getStart());
            int length = request.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<ModuleEntity> spec = moduleSpecificationBuilder.build(request)
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

            Page<ModuleEntity> modules = moduleRepository.findAll(spec, pageable);
    
            return ResponseEntity.ok(
                DataTableResponse.from(modules.map(module -> ModuleDTO.builder()
                    .id(module.getId())
                    .name(module.getName())
                    .description(module.getDescription())
                    .url(module.getUrl())
                    .icon(module.getIcon())
                    .position(module.getPosition())
                    .status(module.getStatus())
                    .menus(
                        null
                    )
                    .build()), request.getDraw())
            );
    
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
            );
        }
    }

    public ResponseEntity<?> getModules(ModuleDTO request) {
        try {
            List<ModuleEntity> modules = moduleRepository.findAllByStatus(ModelStatus.ACTIVE);
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Módulos obtenidos correctamente"), Optional.of(modules))
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al obtener los módulos"))
            );
        }
    }

    public ResponseEntity<?> getModulesMenu() {
        try {

            

            List<ModuleEntity> modules = moduleRepository.findActiveModulesWithActiveMenus(parseStatus(ModelStatus.ACTIVE), parseStatus(MenuStatus.ACTIVE));

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
                                    menuService.getMenusByModuleId(module.getId()))
                            .build())
                    .toList();

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Módulos obtenidos correctamente"), Optional.of(moduleDTOs)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    public ResponseEntity<?> storeModule(
            @Valid @RequestBody ModuleDTO request,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        try {
            if (moduleRepository.existsByNameAndDeletedAtIsNull(request.getName())) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El nombre del módulo ya existe"))
                );
            }
            if (moduleRepository.existsByUrlAndDeletedAtIsNull(request.getUrl())) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("La url del módulo ya existe"))
                );
            }
            moduleRepository.save(ModuleEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .url(request.getUrl())
                .icon(request.getIcon())
                .position(request.getPosition())
                .status(request.getStatus())
                .build());
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Módulo creado correctamente"), Optional.empty()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al guardar el módulo"))
            );
        }
    }

    public ResponseEntity<?> updateModule(
            @Valid @RequestBody ModuleEntity request,
            BindingResult bindingResult) {
        try {
            if (moduleRepository.existsByNameAndIdNot(request.getName(), request.getId())) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El nombre del módulo ya existe"))
                );
            }

            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondJson(bindingResult)
                );
            }

            ModuleEntity module = moduleRepository.findById(request.getId())
                    .orElseThrow(() -> new RuntimeException("Módulo no encontrado"));

            module.setName(request.getName());
            module.setDescription(request.getDescription());
            module.setUrl(request.getUrl());
            module.setIcon(request.getIcon());
            module.setPosition(request.getPosition());
            module.setStatus(request.getStatus());
            module.setUpdatedAt(LocalDateTime.now());
            module = moduleRepository.save(module);
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Módulo actualizado correctamente"), Optional.empty()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
            );
        }
    }

    public ResponseEntity<?> deleteModule(Long id) {
        try {
            ModuleEntity module = moduleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Módulo no encontrado"));
            module.setDeletedAt(LocalDateTime.now());
            module = moduleRepository.save(module);
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Módulo eliminado correctamente"), Optional.empty()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al eliminar el módulo"))
            );
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

    private String parseStatus(MenuStatus status) {
        return status.name();
    }

    private String parseStatus(ModelStatus status) {
        return status.name();
    }
}
