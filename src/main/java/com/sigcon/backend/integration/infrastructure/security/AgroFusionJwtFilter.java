package com.sigcon.backend.integration.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigcon.backend.integration.domain.service.JwtAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-INT-RF-11: Filtro JWT para endpoints AAEF ({@code /api/contabilidad/**}).
 *
 * <p>Ejecuta DESPUES de {@link ApiKeyFilter} en la cadena, pero si el request
 * trae header {@code Authorization: Bearer <token>} valido, este filtro
 * establece la autenticacion en el {@link SecurityContextHolder} ANTES de que
 * ApiKeyFilter actue (la prioridad se logra con el orden en {@code SecurityConfig}).
 *
 * <p>Codigos HTTP esperados:
 * <ul>
 *   <li>401 + "Token JWT expirado" (E2)</li>
 *   <li>403 + "Scope insuficiente: se requiere aaef:lote:enviar" (E3)</li>
 *   <li>401 + "Issuer no reconocido" (E4)</li>
 *   <li>401 + "Firma JWT invalida" (E5)</li>
 * </ul>
 *
 * <p>HU-INT-RF-12 E4: si llegan ambos headers (Authorization Bearer + X-API-Key),
 * el JWT tiene prioridad. Si el JWT es valido, se acepta. Si falla, NO se
 * reintenta con API Key (politica conservadora: error JWT siempre termina request).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgroFusionJwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PATH_PREFIX = "/api/contabilidad/";
    private static final String ADMIN_PATH_PREFIX = "/api/contabilidad/admin/";

    /**
     * Endpoints del frontend admin que usan JWT del SSO interno (HS256) y NO
     * el JWT de AgroFusion (RS256). El filtro debe saltarlos para que la
     * cadena Spring Security valide contra el oauth2 resource server estandar.
     * Paths: /lotes/**, /transferencias/**.
     */
    private static final String[] ADMIN_UI_PATHS = {
            "/api/contabilidad/lotes",
            "/api/contabilidad/transferencias"
    };

    private final JwtConfigService config;
    private final AgroFusionJwtValidator validator;
    private final ObjectMapper objectMapper;
    /** HU-INT-RF-11 (forensia): registra cada token validado/rechazado. */
    private final JwtAuditService jwtAuditService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Normalizar el path: detras de Dokploy/nginx el URI llega con prefix (/sigcon/dev/...).
        // Cortamos desde /api/contabilidad/ para que la logica funcione independientemente.
        String fullUri = request.getRequestURI();
        int idx = fullUri.indexOf(PATH_PREFIX);
        String path = idx >= 0 ? fullUri.substring(idx) : fullUri;
        // Solo aplica a /api/contabilidad/** (excepto health publico y admin/* protegido por SSO interno).
        if (!path.startsWith(PATH_PREFIX)) return true;
        if (path.equals(PATH_PREFIX + "health")) return true;
        // Endpoints administrativos usan el JWT del SSO interno (oauth2 resource server),
        // no el JWT de AgroFusion. NO procesar aqui para evitar conflicto de Bearer.
        if (path.startsWith(ADMIN_PATH_PREFIX)) return true;
        // Endpoints del frontend admin (/lotes, /transferencias) tambien usan JWT del
        // SSO interno. El contador los consume desde la UI con Bearer HS256, no RS256
        // de AgroFusion. Se saltan para que oauth2ResourceServer los valide estandar.
        for (String adminUi : ADMIN_UI_PATHS) {
            if (path.startsWith(adminUi)) return true;
        }
        if (!config.isEnabled()) return true;
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        // Si no hay Bearer, dejar que ApiKeyFilter maneje X-API-Key (HU-INT-RF-12 fallback)
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            AgroFusionJwtValidator.ValidationResult result = validator.validateDetailed(token);
            JsonNode claims = result.getClaims();
            String scope = config.getScopeRequired();
            // HU-INT-RF-11 (forensia): registrar token validado exitosamente
            jwtAuditService.recordSuccess(claims, result.getKid(), scope, request);
            // Establecer autenticacion con el subject + scope como authority
            String principal = claims.has("sub") ? claims.get("sub").asText() : "agrofusion";
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null,
                    List.of(new SimpleGrantedAuthority("SCOPE_" + scope),
                            new SimpleGrantedAuthority("ROLE_AGROFUSION")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            // Stripear el header Authorization para que el OAuth2 Resource Server downstream
            // no intente re-decodificar el token con su propio JwtDecoder y lo rechace.
            chain.doFilter(stripAuthorizationHeader(request), response);
        } catch (AgroFusionJwtValidator.JwtValidationException e) {
            // HU-INT-RF-11 (forensia): registrar token rechazado con motivo
            jwtAuditService.recordFailure(e.getType().name(), e.getMessage(), request);
            SecurityContextHolder.clearContext();
            sendError(response, e);
        } catch (Exception e) {
            log.error("Error validando JWT", e);
            jwtAuditService.recordFailure(
                    AgroFusionJwtValidator.ErrorType.INVALID_SIGNATURE.name(),
                    "Error inesperado: " + e.getMessage(),
                    request);
            SecurityContextHolder.clearContext();
            sendError(response, new AgroFusionJwtValidator.JwtValidationException(
                    AgroFusionJwtValidator.ErrorType.INVALID_SIGNATURE,
                    "Error validando JWT: " + e.getMessage()));
        }
    }

    /**
     * Envuelve la request para esconder el header Authorization. Necesario para
     * que el filtro de OAuth2 Resource Server downstream no intente decodificar
     * nuevamente el JWT con su propio JwtDecoder (que apunta a otra fuente).
     */
    private HttpServletRequest stripAuthorizationHeader(HttpServletRequest request) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if ("Authorization".equalsIgnoreCase(name)) return null;
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if ("Authorization".equalsIgnoreCase(name)) return Collections.emptyEnumeration();
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                java.util.List<String> names = new java.util.ArrayList<>();
                Enumeration<String> orig = super.getHeaderNames();
                while (orig.hasMoreElements()) {
                    String h = orig.nextElement();
                    if (!"Authorization".equalsIgnoreCase(h)) names.add(h);
                }
                return Collections.enumeration(names);
            }
        };
    }

    private void sendError(HttpServletResponse response,
                            AgroFusionJwtValidator.JwtValidationException e) throws IOException {
        int status = switch (e.getType()) {
            case INVALID_SCOPE -> 403;
            case JWKS_UNAVAILABLE -> 500;
            default -> 401;
        };
        response.setStatus(status);
        response.setContentType("application/json");
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("errorCode", e.getType().name());
        body.put("message", e.getMessage());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
