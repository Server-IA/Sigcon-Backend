package com.sigcon.backend.parametrization.users.interfaces;

import com.sigcon.backend.parametrization.users.application.auth.AuthRequest;
import com.sigcon.backend.parametrization.users.application.auth.ResetPasswordRequest;
import com.sigcon.backend.parametrization.users.domain.service.AuthService;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "1. Módulo de Parametrización - Autenticación")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        return authService.login(request);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody AuthRequest request) {
        try {
            authService.sendResetPasswordLink(request);
            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("Se ha enviado un enlace para restablecer la contraseña."), Optional.empty()));
        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
            throw __tie;
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request);
            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("La contraseña se ha restablecido correctamente."), Optional.empty()));
        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
            throw __tie;
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return authService.logout(token);
    }

    /**
     * QA Bloque AT (HU-PA-13, 2026-05-13): refresh runtime de los permisos
     * efectivos (rol + permisos temporales ACTIVE dentro de su ventana) del
     * usuario autenticado. El frontend lo invoca despues de operaciones que
     * pueden alterar permisos del usuario actual (recibir un permiso temporal,
     * verlo expirar) sin requerir cierre de sesion.
     *
     * <p>Si el JWT no expone el principal, retorna 401.
     */
    @GetMapping("/me/effective-permissions")
    public ResponseEntity<?> getMyEffectivePermissions(
            org.springframework.security.core.Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("No autenticado.")));
        }
        Map<String, Object> data = Map.of(
                "effectivePermissions", authService.getMyEffectivePermissions(auth.getName())
        );
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Permisos efectivos del usuario actual."),
                        Optional.of(data)));
    }

}
