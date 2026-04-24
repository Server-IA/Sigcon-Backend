package com.sigcon.backend.integration.infrastructure.security;

import com.sigcon.backend.parametrization.parameters.domain.repository.ParameterRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * HU-INT-RF-12: Filter que valida el header {@code X-API-Key} en los endpoints
 * de integracion AAEF.
 *
 * <p>Se aplica SOLO a rutas bajo {@code /api/contabilidad/} (excepto {@code /health}
 * que es publico por RF-INT-12 R05). El resto de la aplicacion sigue usando JWT.
 *
 * <p>La API Key valida se lee del parametro {@code AGROFUSION_API_KEY} en la tabla
 * {@code parameters} (categoria INTEGRATION_AGROFUSION). Se resuelve en cada request
 * para permitir rotacion sin reiniciar la app.
 *
 * <p>En Fase 6 se agregara soporte JWT RS256 con JWKS (HU-INT-RF-11); por ahora solo
 * API Key.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String API_KEY_PARAMETER_NAME = "AGROFUSION_API_KEY";

    /** Paths protegidos por API Key. */
    private static final String AAEF_PATH_PREFIX = "/api/contabilidad/";

    /** Path publico dentro del prefix (no requiere autenticacion). */
    private static final String HEALTH_PATH = "/api/contabilidad/health";

    /** Endpoints administrativos: usan JWT del SSO interno + ROLE_ADMIN, no API Key. */
    private static final String ADMIN_PATH_PREFIX = "/api/contabilidad/admin/";

    /**
     * Endpoints del frontend admin (/lotes y /transferencias) consumidos por el
     * contador autenticado con JWT del SSO interno, no por AgroFusion. Se saltan
     * este filtro para que oauth2ResourceServer valide estandar.
     */
    private static final String[] ADMIN_UI_PATHS = {
            "/api/contabilidad/lotes",
            "/api/contabilidad/transferencias"
    };

    private final ParameterRepository parameterRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain)
            throws ServletException, IOException {

        // Normalizar el path: si Dokploy/nginx antepone un prefix tipo /sigcon/dev,
        // getRequestURI() devuelve "/sigcon/dev/api/contabilidad/aaef" y el filtro fallaria
        // con startsWith. Cortamos desde /api/contabilidad/ para que la logica funcione
        // tanto detras de proxy como en local sin prefix.
        String fullUri = request.getRequestURI();
        int idx = fullUri.indexOf(AAEF_PATH_PREFIX);
        String path = idx >= 0 ? fullUri.substring(idx) : fullUri;

        // Solo aplicar a rutas /api/contabilidad/* (excepto /health publico, admin/*
        // que usa JWT del SSO interno + @PreAuthorize ROLE_ADMIN, y /lotes /transferencias
        // que usan JWT del SSO interno + @PreAuthorize).
        if (!path.startsWith(AAEF_PATH_PREFIX)
                || path.equals(HEALTH_PATH)
                || path.startsWith(ADMIN_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        for (String adminUi : ADMIN_UI_PATHS) {
            if (path.startsWith(adminUi)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // HU-INT-RF-12 E4: Si AgroFusionJwtFilter ya establecio una autenticacion
        // valida (Authorization: Bearer), JWT tiene prioridad y se salta API Key.
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        // Extraer header X-API-Key
        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey == null || providedKey.trim().isEmpty()) {
            // HU-INT-RF-01 E3: mensaje exacto del Excel
            reject(response, 401, "Credenciales inválidas o ausentes");
            return;
        }

        // Leer API Key GLOBAL de AgroFusion desde tabla parameters.
        // Multi-tenant: AgroFusion autentica con UNA sola key cross-empresa; el
        // enrutamiento a la empresa destino se hace despues por el NIT del payload
        // (ver AaefReceiverService). Por eso usamos query nativa que bypasea el
        // @Filter("tenantFilter") y devuelve la key de la empresa con id mas bajo
        // (convencion: SIGCON DEMO id=1 es la fuente autoritativa de config global).
        Optional<String> expectedKeyOpt = parameterRepository
                .findGlobalValueByName(API_KEY_PARAMETER_NAME);

        if (expectedKeyOpt.isEmpty() || expectedKeyOpt.get().trim().isEmpty()) {
            log.error("AGROFUSION_API_KEY global no esta configurado en tabla parameters");
            reject(response, 500, "API Key del sistema no configurado");
            return;
        }

        String expectedKey = expectedKeyOpt.get();

        // Comparacion de tiempo constante para evitar timing attacks
        if (!constantTimeEquals(providedKey, expectedKey)) {
            log.warn("Intento de autenticacion con API Key invalida desde {}",
                    request.getRemoteAddr());
            // HU-INT-RF-01 E3: mensaje generico para no revelar si la key existe
            reject(response, 401, "Credenciales inválidas o ausentes");
            return;
        }

        // Autenticacion OK: establecer auth en SecurityContext para que
        // UserUtil.getUser() y otros componentes tengan usuario identificado.
        // El TenantContext se setea DESPUES en AaefReceiverService por el NIT del payload.
        var auth = new UsernamePasswordAuthenticationToken(
                "agrofusion-api-key", null,
                List.of(new SimpleGrantedAuthority("ROLE_AGROFUSION_API_KEY")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }

    /**
     * Comparacion de tiempo constante para evitar timing attacks.
     * No retorna temprano aunque los strings difieran en el primer caracter.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private void reject(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"success\":false,\"code\":" + status
                + ",\"error\":\"Autenticacion AAEF\",\"message\":\"" + message
                + "\",\"data\":null}");
    }
}
