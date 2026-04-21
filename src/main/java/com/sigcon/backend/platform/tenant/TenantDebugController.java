package com.sigcon.backend.platform.tenant;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Endpoint de diagnostico del multi-tenant. Devuelve como se resolvio el
 * TenantContext para el usuario autenticado (companyId, platformAdmin, userId
 * del JWT, authorities).
 *
 * <p>Util durante el desarrollo/validacion del Bloque A. En produccion
 * se puede dejar porque devuelve datos del propio usuario autenticado
 * (no expone nada de otras empresas).
 */
@RestController
@RequestMapping("/api/platform")
@Tag(name = "Platform - Debug Tenant", description = "Introspección del TenantContext del usuario autenticado")
public class TenantDebugController {

    @GetMapping("/me")
    @Operation(summary = "Retorna info del tenant context + claims JWT del usuario actual")
    public ResponseEntity<Map<String, Object>> me() {
        Map<String, Object> body = new HashMap<>();
        body.put("tenantContext.companyId",     TenantContext.getCompanyId());
        body.put("tenantContext.platformAdmin", TenantContext.isPlatformAdmin());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            body.put("jwt.sub",          jwt.getSubject());
            body.put("jwt.userId",       jwt.getClaim("userId"));
            body.put("jwt.companyId",    jwt.getClaim("companyId"));
            body.put("jwt.platformRole", jwt.getClaim("platformRole"));
            body.put("jwt.authorities",  jwt.getClaim("authorities"));
        }
        return ResponseEntity.ok(body);
    }
}
