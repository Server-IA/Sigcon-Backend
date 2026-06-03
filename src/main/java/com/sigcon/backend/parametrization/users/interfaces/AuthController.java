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
    public ResponseEntity<?> login(@RequestBody AuthRequest request,
                                   jakarta.servlet.http.HttpServletRequest http) {
        // PA-RF-01 v3.0: pasar IP (X-Forwarded-For o remote addr) y User-Agent
        // a la capa de servicio para registrarlos en la sesion activa.
        return authService.login(request, clientIp(http), http.getHeader("User-Agent"));
    }

    /**
     * PA-RF-01 v3.0 (Control de Cambios PA, 2026-05-29): renueva el access token
     * a partir del refresh token entregado en el login.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @RequestBody com.sigcon.backend.parametrization.users.application.auth.RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    /** Extrae la IP real del cliente respetando proxy (X-Forwarded-For, primer hop). */
    private static String clientIp(jakarta.servlet.http.HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return http.getRemoteAddr();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody AuthRequest request,
                                            jakarta.servlet.http.HttpServletRequest http) {
        try {
            authService.sendResetPasswordLink(request, clientIp(http));
        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
            throw __tie;
        } catch (com.sigcon.backend.parametrization.users.application.auth.TooManyRequestsException tmr) {
            // PA-RF-02 punto 2 (v3.0): rate limit excedido -> 429.
            return ResponseEntity.status(429).body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(tmr.getMessage())));
        } catch (Exception e) {
            // PA-RF-02 punto 1 (v3.0): ANTI-ENUMERACION. Ningun error interno debe
            // revelarse; el servicio ya traga el caso "email no existe" y los
            // errores de envio de correo, asi que esto es solo una red de seguridad.
        }
        // Respuesta generica identica exista o no el email (anti-enumeracion).
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Si el correo está registrado, se ha enviado un enlace para restablecer la contraseña."),
                        Optional.empty()));
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
            // PA-RF-02 (v3.0): los errores de reset (token invalido/expirado,
            // contrasena que no cumple la politica, reutilizacion) son errores
            // del cliente -> HTTP 400 (antes 500).
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader,
                                    jakarta.servlet.http.HttpServletRequest http) {
        String token = authHeader.replace("Bearer ", "");
        // PA-RF-27 punto 5: pasar la IP del cliente para que quede en el audit del LOGOUT.
        return authService.logout(token, clientIp(http));
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
        // QA Bloque AV (HU-PA-13 E7 regla #11, 2026-05-14): response ahora trae
        // 3 campos: rolePermissions, temporaryPermissions, effectivePermissions
        // (union). El frontend usa rolePermissions para decidir visibilidad de
        // botones como "Asignar permiso temporal" cuya HU exige source=rol.
        // Backward-compat: effectivePermissions sigue siendo la union (codigo
        // viejo que solo leia ese campo no se rompe).
        Map<String, Object> data = authService.getMyEffectivePermissionsSplit(auth.getName());
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Permisos efectivos del usuario actual."),
                        Optional.of(data)));
    }

}
