package com.sigcon.backend.general.security;

import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * QA Bloque PA Bug 79 (HU-PA-11 E4, 2026-05-11): invalidacion dinamica de
 * sesion.
 *
 * <p>Spring Security usa {@code oauth2ResourceServer.jwt()} con un
 * {@code JwtDecoder} estandar que valida firma + expiracion. NO consulta la
 * BD por cada request. Resultado: si un admin cambia los roles/status del
 * usuario A, las sesiones activas de A (con token JWT vivo emitido antes del
 * cambio) siguen funcionando con los permisos viejos hasta que el token
 * expire.
 *
 * <p>Este filter corre DESPUES de {@code BearerTokenAuthenticationFilter},
 * con la autenticacion ya establecida. Compara el claim {@code iat} (issued
 * at) del token con la columna {@code users.session_invalidated_at} del
 * usuario. Si el token fue emitido ANTES de la invalidacion, limpia el
 * SecurityContext y devuelve 401 — forzando re-login para que los permisos
 * actuales tengan efecto.
 *
 * <p>El filter es defensivo: cualquier excepcion al cargar el user de BD
 * NO bloquea el request (logging warning + continuar con auth normal).
 * La invalidacion solo se aplica si BD confirma sessionInvalidatedAt > iat.
 *
 * <p>Performance: agrega 1 query simple por request autenticado. Se podria
 * cachear en memoria con TTL corto si fuera cuello de botella, pero por
 * ahora la operacion es trivial (PK lookup + comparacion timestamp).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE - 20) // antes que TenantContextFilter
public class SessionInvalidationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            try {
                String username = jwt.getSubject();
                Instant iat = jwt.getIssuedAt();
                if (username != null && iat != null) {
                    // findByUsername no existe; usar findByUsernameOrEmail
                    Optional<User> opt = userRepository.findByUsernameOrEmail(username, username);
                    if (opt.isPresent()) {
                        User u = opt.get();
                        if (u.getSessionInvalidatedAt() != null) {
                            Instant cutoff = u.getSessionInvalidatedAt()
                                    .atZone(ZoneId.systemDefault()).toInstant();
                            if (iat.isBefore(cutoff)) {
                                // Token obsoleto: limpiar auth y responder 401 literal de la HU.
                                SecurityContextHolder.clearContext();
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json;charset=UTF-8");
                                response.getWriter().write(
                                    "{\"success\":false,\"code\":401,"
                                  + "\"message\":\"Su sesion expiro porque un administrador modifico "
                                  + "sus roles o permisos. Vuelva a iniciar sesion para que los "
                                  + "cambios surtan efecto.\","
                                  + "\"error\":\"SESSION_INVALIDATED\"}");
                                return;
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                // Defensivo: no bloquear request por error de BD; permitir continuar
                // con auth normal. El proximo request reintentara la validacion.
                log.debug("SessionInvalidationFilter skip: {}", ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
