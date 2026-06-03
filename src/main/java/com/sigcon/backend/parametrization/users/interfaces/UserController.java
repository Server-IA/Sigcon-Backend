package com.sigcon.backend.parametrization.users.interfaces;

import com.sigcon.backend.parametrization.users.application.user.UserDTO;
import com.sigcon.backend.utils.DataTableRequest;

import jakarta.validation.Valid;

import com.sigcon.backend.parametrization.users.domain.service.UserService;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "1. Módulo de Parametrización - Usuarios", description = "Endpoints para la gestión de usuarios")
public class UserController {

    private final UserService userService;

    @PostMapping("/getUsers")
    @PreAuthorize("hasAuthority('PERM_VIEW_USERS') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getUsers(@RequestBody(required = false) DataTableRequest request) {
        return userService.getUsers(request);
    }

    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_USER')  or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> store(@Valid @RequestBody UserDTO request, BindingResult bindingResult) {
        return userService.store(request, bindingResult);
    }

    @GetMapping
    public ResponseEntity<?> getUserInfo() {
        return userService.getUserInfo();
    }

    @PutMapping("/updateInfo")
    public ResponseEntity<?> updateInfo(@RequestBody UserDTO request) {
        return userService.updateInfo(request);
    }

    @PutMapping("/updateUser/{userId}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_USER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable Long userId, @RequestBody UserDTO request) {
        return userService.updateUser(userId, request);
    }

    @PostMapping("/deleteUser/{userId}")
    @PreAuthorize("hasAuthority('PERM_DELETE_USER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId,
                                        @RequestParam(value = "reason", required = false) String reasonParam,
                                        @RequestBody(required = false) java.util.Map<String, Object> body) {
        // PA-RF-30: el motivo de desactivacion puede venir como query param o en el
        // cuerpo {reason}. El frontend de administracion lo exige (minimo 30 chars).
        String reason = reasonParam;
        if ((reason == null || reason.isBlank()) && body != null && body.get("reason") != null) {
            reason = String.valueOf(body.get("reason"));
        }
        return userService.deleteUser(userId, reason);
    }

    @GetMapping("/avatars/{filename}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {

        try {

            Path basePath = Paths.get("uploads/avatars").toAbsolutePath().normalize();
            Path filePath = basePath.resolve(filename).normalize();

            // 🔐 Evita path traversal
            if (!filePath.startsWith(basePath)) {
                return ResponseEntity.badRequest().build();
            }

            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(filePath);

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(resource);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

}
