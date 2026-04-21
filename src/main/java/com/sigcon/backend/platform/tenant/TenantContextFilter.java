package com.sigcon.backend.platform.tenant;

import java.io.IOException;

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
import lombok.extern.slf4j.Slf4j;

/**
 * Extrae del JWT del SSO interno los claims tenant-aware
 * ({@code companyId}, {@code platformRole}) y los setea en {@link TenantContext}
 * para el resto de la cadena de filtros / controllers / servicios.
 *
 * <p>Se ejecuta despues de que {@code oauth2ResourceServer.jwt()} ya haya
 * autenticado al usuario (por eso {@code Order(LOWEST_PRECEDENCE)} relativo
 * al chain de Spring Security: corremos al final cuando ya hay Authentication).
 *
 * <p>Si no hay autenticacion (request anonimo a un endpoint publico como
 * {@code /health}), no se toca el TenantContext — simplemente se deja vacio.
 * Cualquier consulta tenant-scoped sin contexto se bloquea mas arriba por el
 * filtro Hibernate (cuando se implemente en el Bloque B).
 *
 * <p>{@code TenantContext.clear()} en el finally es critico: evita que un
 * thread del pool de Tomcat hereda tenant de un request previo.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String CLAIM_COMPANY_ID    = "companyId";
    public static final String CLAIM_PLATFORM_ROLE = "platformRole";
    public static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
                resolveTenantFromJwt(jwt);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void resolveTenantFromJwt(Jwt jwt) {
        Object platformRoleClaim = jwt.getClaim(CLAIM_PLATFORM_ROLE);
        if (PLATFORM_ADMIN_ROLE.equals(platformRoleClaim)) {
            TenantContext.setPlatformAdmin(true);
            TenantContext.setCompanyId(null);
            log.trace("Tenant context: PLATFORM_ADMIN (sin company)");
            return;
        }

        Object companyClaim = jwt.getClaim(CLAIM_COMPANY_ID);
        Long companyId = toLong(companyClaim);
        if (companyId != null) {
            TenantContext.setCompanyId(companyId);
            TenantContext.setPlatformAdmin(false);
            log.trace("Tenant context: companyId={}", companyId);
        }
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
