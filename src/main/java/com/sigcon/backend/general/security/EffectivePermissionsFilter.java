package com.sigcon.backend.general.security;

import com.sigcon.backend.parametrization.temporary_permissions.domain.service.TemporaryPermissionService;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * QA Bloque AV (HU-PA-11 E4 + HU-PA-12 E4 + HU-PA-13 E7, 2026-05-14): recomputa
 * los authorities efectivos del usuario en CADA request, sin requerir re-login.
 *
 * <p>Problema que resuelve:
 * <ul>
 *   <li>El JWT cachea las authorities del rol al emitirse. Si un admin
 *       modifica el rol despues, los usuarios con tokens activos siguen viendo
 *       las authorities viejas hasta que su token expire o se invalide la
 *       sesion.</li>
 *   <li>Los permisos temporales (HU-PA-13) se otorgan post-login y NO aparecen
 *       en el JWT. El usuario no puede ejercerlos hasta hacer logout/login.</li>
 *   <li>El fix anterior (sessionInvalidatedAt -> 401) EXPULSABA la sesion ante
 *       cualquier cambio de permisos, violando HU-PA-11 E4 + HU-PA-12 E4 que
 *       exigen que el usuario PERMANEZCA en sesion y solo se recomputen
 *       permisos en su siguiente request.</li>
 * </ul>
 *
 * <p>Comportamiento del filter:
 * <ol>
 *   <li>Lee el JWT autenticado del SecurityContext.</li>
 *   <li>Carga el {@link User} de BD por su username/email.</li>
 *   <li>Construye el set efectivo: authorities del rol + permisos temporales
 *       ACTIVE con prefijo distintivo {@code TEMP_} (para que la regla #11
 *       pueda diferenciar fuente cuando se necesite, ej. ASIGNAR/REVOCAR
 *       permisos temporales solo aceptan {@code PERM_} - rol).</li>
 *   <li>Crea un nuevo {@link JwtAuthenticationToken} con esos authorities
 *       recomputados y lo reemplaza en el SecurityContext.</li>
 * </ol>
 *
 * <p>Convencion de prefijos:
 * <ul>
 *   <li>{@code PERM_<CODE>}: permiso del rol (estable, exige re-login para
 *       cambiar).</li>
 *   <li>{@code TEMP_<CODE>}: permiso temporal activo en este momento (delegado
 *       por admin via HU-PA-13).</li>
 *   <li>{@code ROLE_<NAME>}: rol del usuario.</li>
 *   <li>{@code PLATFORM_ADMIN}: rol cross-tenant.</li>
 * </ul>
 *
 * <p>Endpoints que requieren CADA fuente:
 * <ul>
 *   <li>{@code hasAuthority('PERM_X')} - solo el rol habilita (ej. ASIGNAR
 *       permiso temporal: regla #11 - no debe poder asignarse recursivamente
 *       con un temporal).</li>
 *   <li>{@code hasAnyAuthority('PERM_X','TEMP_X')} - rol o temporal lo
 *       habilitan (ej. VER, READ_LIST, EXPORT).</li>
 * </ul>
 *
 * <p>Performance: 1 query a {@code users} + 1 query a {@code temporary_permissions}
 * por request autenticado. Defensivo: si falla la carga, deja pasar el request
 * con los authorities del JWT (degradacion controlada, no expulsion).
 *
 * <p>Orden de filtros: corre DESPUES de {@link SessionInvalidationFilter} y
 * DESPUES de {@link com.sigcon.backend.platform.tenant.TenantContextFilter}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE - 5)
public class EffectivePermissionsFilter extends OncePerRequestFilter {

    /** Prefijo distintivo para authorities provenientes de permisos temporales. */
    public static final String TEMP_PREFIX = "TEMP_";
    /** Prefijo estable para authorities provenientes del rol. */
    public static final String ROLE_PERM_PREFIX = "PERM_";

    private final UserRepository userRepository;
    private final TemporaryPermissionService temporaryPermissionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            try {
                String username = jwt.getSubject();
                if (username != null) {
                    Optional<User> opt = userRepository.findByUsernameOrEmail(username, username);
                    if (opt.isPresent()) {
                        User u = opt.get();
                        Set<GrantedAuthority> fresh = computeFreshAuthorities(u, jwt);
                        // Reemplazar Authentication con authorities recomputados.
                        AbstractAuthenticationToken refreshed =
                                new JwtAuthenticationToken(jwt, fresh);
                        // Preserva detalles del request (ip, sessionId).
                        refreshed.setDetails(auth.getDetails());
                        SecurityContextHolder.getContext().setAuthentication(refreshed);
                    }
                }
            } catch (Exception ex) {
                // Defensivo: no romper request si BD/temporales fallan. El
                // usuario sigue con sus authorities del JWT (state previo).
                log.debug("EffectivePermissionsFilter skip: {}", ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Construye el set fresco de authorities del usuario.
     *
     * <p>Incluye:
     * <ul>
     *   <li>Authorities del JWT que NO son permisos (ROLE_*, PLATFORM_ADMIN).
     *       Estas se mantienen tal cual estan en el token: son estables y
     *       cambian solo en re-login.</li>
     *   <li>Permisos del rol recomputados desde BD con prefijo {@code PERM_}.
     *       Si el admin removio un permiso del rol, este filter lo refleja
     *       inmediatamente sin esperar a expiracion del JWT.</li>
     *   <li>Permisos temporales activos AHORA con prefijo {@code TEMP_}.</li>
     * </ul>
     */
    private Set<GrantedAuthority> computeFreshAuthorities(User user, Jwt jwt) {
        Set<GrantedAuthority> result = new LinkedHashSet<>();

        // 1. Conservar ROLE_* y PLATFORM_ADMIN del JWT (estables).
        if (jwt.getClaims() != null) {
            Object authClaim = jwt.getClaims().get("authorities");
            if (authClaim instanceof java.util.List<?> list) {
                for (Object o : list) {
                    String s = String.valueOf(o);
                    // Solo conservar non-perm authorities. Los permisos los
                    // recomputamos de BD para no quedar con permisos viejos.
                    if (s != null && !s.startsWith(ROLE_PERM_PREFIX) && !s.startsWith(TEMP_PREFIX)) {
                        result.add(new SimpleGrantedAuthority(s));
                    }
                }
            }
        }

        // 2. Permisos del rol (recomputados de BD).
        if (user.getRoles() != null) {
            for (var role : user.getRoles()) {
                if (role == null) continue;
                if (role.getName() != null && !role.getName().isBlank()) {
                    result.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
                }
                if (role.getPermissions() != null) {
                    for (var p : role.getPermissions()) {
                        if (p == null || p.getCode() == null) continue;
                        String code = p.getCode();
                        // Normalizar: si el code ya viene con PERM_, no duplicar.
                        String authority = code.startsWith(ROLE_PERM_PREFIX)
                                ? code
                                : (ROLE_PERM_PREFIX + code);
                        result.add(new SimpleGrantedAuthority(authority));
                    }
                }
            }
        }

        // 3. Permisos temporales activos -> prefijo TEMP_.
        try {
            Set<String> tempCodes = temporaryPermissionService.computeEffectiveCodes(user.getId());
            if (tempCodes != null) {
                for (String code : tempCodes) {
                    if (code == null || code.isBlank()) continue;
                    // Aceptar codes con o sin PERM_ prefix. Agregamos AMBOS
                    // formatos como TEMP_ para que cualquier @PreAuthorize lo
                    // detecte: TEMP_PERM_X (con perm) y TEMP_X (sin perm).
                    if (code.startsWith(ROLE_PERM_PREFIX)) {
                        // PERM_PAR.PERMISOS_TEMPORALES.VER -> TEMP_PERM_PAR.PERMISOS_TEMPORALES.VER
                        result.add(new SimpleGrantedAuthority(TEMP_PREFIX + code));
                        // Tambien sin PERM_: TEMP_PAR.PERMISOS_TEMPORALES.VER
                        result.add(new SimpleGrantedAuthority(TEMP_PREFIX + code.substring(ROLE_PERM_PREFIX.length())));
                    } else {
                        // PAR.PERMISOS_TEMPORALES.VER -> TEMP_PAR.PERMISOS_TEMPORALES.VER
                        result.add(new SimpleGrantedAuthority(TEMP_PREFIX + code));
                        // Tambien con PERM_: TEMP_PERM_PAR.PERMISOS_TEMPORALES.VER
                        result.add(new SimpleGrantedAuthority(TEMP_PREFIX + ROLE_PERM_PREFIX + code));
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("EffectivePermissionsFilter: error computing temporal codes for userId={}: {}",
                    user.getId(), ex.getMessage());
        }

        return result;
    }
}
