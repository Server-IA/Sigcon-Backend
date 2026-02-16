package com.sigcon.backend.parametrization.menuPermissions.interfaces;

import java.util.Optional;

import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.parametrization.menuPermissions.application.MenuPermissionsDTO;
import com.sigcon.backend.parametrization.menuPermissions.domain.service.MenuPermissionsService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;


@RestController
@RequestMapping("/api/menu-permissions")
@RequiredArgsConstructor


public class MenuPermissionsController {

    private final MenuPermissionsService menuPermissionsService;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_MENU_PERMISSIONS')")
    public ResponseEntity<?> getMenuPermissions(
        @RequestBody(required = false) DataTableRequest dtRequest
    ) {
        try{
            return menuPermissionsService.getMenuPermissions(dtRequest);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_MENU_PERMISSIONS')")
    public ResponseEntity<?> storeMenuPermission(@Valid @RequestBody MenuPermissionsDTO request, BindingResult bindingResult) {
        try{
            return menuPermissionsService.storeMenuPermission(request, bindingResult);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @PutMapping("/update")
    @PreAuthorize("hasAuthority('PERM_UPDATE_MENU_PERMISSIONS')")
    public ResponseEntity<?> updateMenuPermission(@RequestBody MenuPermissionsDTO request, BindingResult bindingResult) {
        try{
            return menuPermissionsService.updateMenuPermission(request, bindingResult);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_MENU_PERMISSIONS')")
    public ResponseEntity<?> deleteMenuPermission(@PathVariable Long id) {
        try{
            return menuPermissionsService.deleteMenuPermission(id);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

}
