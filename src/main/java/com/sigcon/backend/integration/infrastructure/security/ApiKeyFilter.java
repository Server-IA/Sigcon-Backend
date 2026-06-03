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
    public static final String API_KEY_TEST_PARAMETER_NAME = "AGROFUSION_API_KEY_TEST";

    /**
     * Atributo del request que indica el "tier" de la API Key validada.
     * Lo lee {@code AaefRateLimitFilter} para aplicar el limite por hora correcto.
     * Valores: {@code "PRODUCTION"} (10/h) o {@code "TEST"} (50/h).
     *
     * <p>Nota: AaefRateLimitFilter corre ANTES de este filter en la cadena, por lo
     * que el rate filter NO puede leer este atributo de forma confiable. Por eso
     * AaefRateLimitFilter resuelve el tier por su cuenta consultando ambas keys.
     * Este atributo se mantiene como debug/observabilidad para handlers downstream.
     */
    public static final String AAEF_KEY_TIER_ATTR = "aaef.key.tier";
    public static final String TIER_PRODUCTION = "PRODUCTION";
    public static final String TIER_TEST = "TEST";

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
    // PA-RF-28 (Pendientes PA): claves gestionadas (tabla api_keys, hash SHA-256).
    private final com.sigcon.backend.integration.apikeys.domain.service.ApiKeyService apiKeyService;

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

        // Leer ambas API Keys GLOBALES desde tabla parameters:
        //   - AGROFUSION_API_KEY      (PRODUCTION, rate limit 10/h - usada por AgroFusion)
        //   - AGROFUSION_API_KEY_TEST (TEST,       rate limit 50/h - usada por QA / integradores)
        //
        // Multi-tenant: las keys son globales cross-empresa; el enrutamiento a la
        // empresa destino se hace despues por el NIT del payload (ver
        // AaefReceiverService). Query nativa bypasea el @Filter("tenantFilter").
        Optional<String> prodKeyOpt = parameterRepository
                .findGlobalValueByName(API_KEY_PARAMETER_NAME);
        Optional<String> testKeyOpt = parameterRepository
                .findGlobalValueByName(API_KEY_TEST_PARAMETER_NAME);

        // Al menos una de las dos debe estar configurada
        boolean prodAvailable = prodKeyOpt.isPresent() && !prodKeyOpt.get().trim().isEmpty();
        boolean testAvailable = testKeyOpt.isPresent() && !testKeyOpt.get().trim().isEmpty();
        if (!prodAvailable && !testAvailable) {
            log.error("Ninguna API Key AAEF (prod/test) esta configurada en parameters");
            reject(response, 500, "API Key del sistema no configurado");
            return;
        }

        // Comparacion de tiempo constante contra ambas keys.
        // Evaluamos las dos para no filtrar por timing si la key recibida es prod o test.
        boolean matchesProd = prodAvailable && constantTimeEquals(providedKey, prodKeyOpt.get());
        boolean matchesTest = testAvailable && constantTimeEquals(providedKey, testKeyOpt.get());

        if (!matchesProd && !matchesTest) {
            // PA-RF-28: si la key global legacy no coincide, intentar contra las
            // claves GESTIONADAS (tabla api_keys, por hash SHA-256). Es ADITIVO:
            // el flujo AAEF con la clave global de AgroFusion sigue intacto y, de
            // forma adicional, se aceptan las claves emitidas con ciclo de vida.
            if (apiKeyService.validateAndTouch(providedKey)) {
                request.setAttribute(AAEF_KEY_TIER_ATTR, TIER_PRODUCTION);
                var managedAuth = new UsernamePasswordAuthenticationToken(
                        "agrofusion-api-key-managed", null,
                        List.of(new SimpleGrantedAuthority("ROLE_AGROFUSION_API_KEY")));
                SecurityContextHolder.getContext().setAuthentication(managedAuth);
                log.debug("AAEF auth OK con API Key gestionada (api_keys)");
                chain.doFilter(request, response);
                return;
            }
            log.warn("Intento de autenticacion con API Key invalida desde {}",
                    request.getRemoteAddr());
            // HU-INT-RF-01 E3: mensaje generico para no revelar si la key existe
            reject(response, 401, "Credenciales inválidas o ausentes");
            return;
        }

        // Stamp del tier en el request para downstream / observabilidad
        String tier = matchesProd ? TIER_PRODUCTION : TIER_TEST;
        request.setAttribute(AAEF_KEY_TIER_ATTR, tier);
        log.debug("AAEF auth OK con API Key tier={}", tier);

        // Autenticacion OK: establecer auth en SecurityContext con authority diferenciada
        // por tier (util si en el futuro se quiere @PreAuthorize por tier).
        String authority = matchesProd ? "ROLE_AGROFUSION_API_KEY" : "ROLE_AGROFUSION_API_KEY_TEST";
        String principal = matchesProd ? "agrofusion-api-key" : "agrofusion-api-key-test";
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority(authority)));
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
