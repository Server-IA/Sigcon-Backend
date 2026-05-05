package com.sigcon.backend.parametrization.notifications.infrastructure;

import com.sigcon.backend.general.security.JwtService;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HU-PA-21 push: filter que autentica conexiones SSE leyendo el JWT del query
 * param {@code ?token=}.
 *
 * <p>Justificacion: el {@code EventSource} del navegador NO permite agregar
 * headers custom. Sin este filter, el endpoint SSE devuelve 401 cuando el
 * cliente intenta conectarse en produccion (donde no hay session cookie).
 *
 * <p>Solo se ejecuta en el path {@code /api/parametrization/notifications/stream}
 * (con o sin prefijo de proxy). Cualquier otro path lo deja pasar para que
 * lo procesen los filtros normales.
 *
 * <p>Si el token es invalido o esta vencido, no autentica - el oauth2 resource
 * server downstream rechaza con 401. Si el token es valido, el SecurityContext
 * queda con el user listo para que {@code stream()} funcione.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SseTokenAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.endsWith("/notifications/stream");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Si ya hay auth en el contexto, no hacemos nada (Bearer header tuvo prioridad)
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        String token = request.getParameter("token");
        if (token == null || token.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String username = jwtService.getUsername(token);
            if (username == null) {
                chain.doFilter(request, response);
                return;
            }
            User user = userRepository.findByUsernameOrEmail(username, username).orElse(null);
            if (user == null || user.getDeletedAt() != null || !jwtService.validateToken(token, user)) {
                chain.doFilter(request, response);
                return;
            }
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("[SSE] auth via query token user={}", username);
        } catch (RuntimeException ex) {
            log.debug("[SSE] token query invalido: {}", ex.getMessage());
            // No bloqueamos - oauth2 resource server downstream respondera 401
        }
        chain.doFilter(request, response);
    }
}
