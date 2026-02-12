package com.sigcon.backend.parametrization.menuPermissions.domain.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;
import com.sigcon.backend.parametrization.menuPermissions.application.MenuPermissionsDTO;
import com.sigcon.backend.parametrization.menuPermissions.domain.model.MenuPermissionsEntity;
import com.sigcon.backend.parametrization.menuPermissions.domain.repository.MenuPermissionsRepository;
import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor

public class MenuPermissionsService {

    private final MenuPermissionsRepository menuPermissionsRepository;

    private final DataTableSpecificationBuilder<MenuPermissionsEntity> menuPermissionsSpecificationBuilder =
        new DataTableSpecificationBuilder<>();

    public ResponseEntity<?> getMenuPermissions(DataTableRequest request){
        try{
            int start = Math.max(0, request.getStart());
            int length = request.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<MenuPermissionsEntity> spec = menuPermissionsSpecificationBuilder.build(request)
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

            Page<MenuPermissionsEntity> menuPermissions = menuPermissionsRepository.findAll(spec, pageable);

            return ResponseEntity.ok(
                DataTableResponse.from(menuPermissions.map(menuPermission -> MenuPermissionsDTO.builder()
                    .id(menuPermission.getId())
                    .menu_id(menuPermission.getMenu().getId())
                    .role_id(menuPermission.getRole().getId())
                    .menu(
                        menuPermission.getMenu().getLabel()
                    )
                    .role(
                        menuPermission.getRole().getName()
                    )
                    .createdAt(menuPermission.getCreatedAt())
                    .updatedAt(menuPermission.getUpdatedAt())
                    .build()), request.getDraw())
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(e.getMessage()));
        }
    }

    
    public ResponseEntity<?> storeMenuPermission(MenuPermissionsDTO request, BindingResult bindingResult) {
        try{

            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(bindingResult.getAllErrors().toString()));
            }   

            MenuPermissionsEntity menuPermissions = MenuPermissionsEntity.builder()
                .menu(MenuEntity.builder().id(request.getMenu_id()).build())
                .role(Role.builder().id(request.getRole_id()).build())
                .build();

            return ResponseEntity.ok(menuPermissionsRepository.save(menuPermissions));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(e.getMessage()));
        }
    }

    public ResponseEntity<?> updateMenuPermission(MenuPermissionsDTO request, BindingResult bindingResult){
        try{
            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(bindingResult.getAllErrors().toString()));
            }

            MenuPermissionsEntity menuPermission = menuPermissionsRepository.findById(request.getId()).orElseThrow(() -> new RuntimeException("No se encontró el permiso de menú"));

            menuPermission.setMenu(MenuEntity.builder().id(request.getMenu_id()).build());
            menuPermission.setRole(Role.builder().id(request.getRole_id()).build());
            return ResponseEntity.ok(menuPermissionsRepository.save(menuPermission));

        }catch (Exception e){
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(e.getMessage()));
        }
    }

    public ResponseEntity<?> deleteMenuPermission(Long id){
        try{
            MenuPermissionsEntity menuPermission = menuPermissionsRepository.findById(id).orElseThrow(() -> new RuntimeException("No se encontró el permiso de menú"));

            menuPermission.setDeletedAt(LocalDateTime.now());

            return ResponseEntity.ok(menuPermissionsRepository.save(menuPermission));


        }catch (Exception e){
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(e.getMessage()));
        }
    }
    
}
