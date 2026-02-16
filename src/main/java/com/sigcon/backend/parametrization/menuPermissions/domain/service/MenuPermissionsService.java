package com.sigcon.backend.parametrization.menuPermissions.domain.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;
import com.sigcon.backend.parametrization.menu.port.out.MenuRepositoryPort;
import com.sigcon.backend.parametrization.menuPermissions.application.MenuPermissionsDTO;
import com.sigcon.backend.parametrization.menuPermissions.domain.model.MenuPermissionsEntity;
import com.sigcon.backend.parametrization.menuPermissions.domain.repository.MenuPermissionsRepository;
import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor

public class MenuPermissionsService {

    private final MenuPermissionsRepository menuPermissionsRepository;
    private final MenuRepositoryPort menuRepositoryPort;

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
                        Menu.builder()
                            .label(menuPermission.getMenu().getLabel())
                            .build()
                    )
                    .role(
                        Role.builder()
                            .id(menuPermission.getRole().getId())
                            .name(menuPermission.getRole().getName())
                            .build()    
                    )
                    .createdAt(menuPermission.getCreatedAt())
                    .updatedAt(menuPermission.getUpdatedAt())
                    .build()), request.getDraw())
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    
    public ResponseEntity<?> storeMenuPermission(MenuPermissionsDTO request, BindingResult bindingResult) {
        try{

            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }
            
            if (menuPermissionsRepository.findByMenuIdAndRoleIdAndDeletedAtIsNull(request.getMenu_id(), request.getRole_id()).isPresent()) {
                throw new IllegalArgumentException("El permiso del menú ya existe");
            }

            MenuPermissionsEntity menuPermissions = MenuPermissionsEntity.builder()
                .menu(MenuEntity.builder().id(request.getMenu_id()).build())
                .role(Role.builder().id(request.getRole_id()).build())
                .build();

            MenuPermissionsEntity savedMenuPermissions = menuPermissionsRepository.save(menuPermissions);   

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Permiso de menú creado correctamente"), Optional.of(savedMenuPermissions)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    public ResponseEntity<?> updateMenuPermission(MenuPermissionsDTO request, BindingResult bindingResult){
        try{
            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }

            MenuPermissionsEntity menuPermission = menuPermissionsRepository.findById(request.getId()).orElseThrow(() -> new RuntimeException("No se encontró el permiso de menú"));

            if (menuPermissionsRepository.findByMenuIdAndRoleIdAndIdNotAndDeletedAtIsNull(
                request.getMenu_id(), request.getRole_id(), request.getId()).isPresent()
            ) {
                throw new IllegalArgumentException("El permiso del menú ya existe");
            }


            menuPermission.setMenu(MenuEntity.builder().id(request.getMenu_id()).build());
            menuPermission.setRole(Role.builder().id(request.getRole_id()).build());
            MenuPermissionsEntity updatedMenuPermissions = menuPermissionsRepository.save(menuPermission);
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Permiso de menú actualizado correctamente"), Optional.of(updatedMenuPermissions)));

        }catch (Exception e){
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    public ResponseEntity<?> deleteMenuPermission(Long id){
        try{
            MenuPermissionsEntity menuPermission = menuPermissionsRepository.findById(id).orElseThrow(() -> new RuntimeException("No se encontró el permiso de menú"));

            menuPermission.setDeletedAt(LocalDateTime.now());
            menuPermissionsRepository.save(menuPermission);

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Permiso de menú eliminado correctamente"), Optional.empty()));


        }catch (Exception e){
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
    
}
