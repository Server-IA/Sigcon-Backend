package com.sigcon.backend.utils.export;

import com.sigcon.backend.parametrization.parameters.domain.service.SystemInfoService;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.platform.companies.domain.model.Company;
import com.sigcon.backend.platform.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.platform.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * QA Bloque BJ (2026-05-17): resolver el {@link ReportHeaderBuilder.ReportContext}
 * a partir del TenantContext + SecurityContext del request actual.
 *
 * <p>Los reportes solo necesitan inyectar este service e invocar
 * {@link #baseContext(String)} con el titulo del reporte, agregando filtros y
 * totales especificos del caso.
 */
@Service
@RequiredArgsConstructor
public class ReportContextResolver {

    private final SystemInfoService systemInfoService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    /**
     * Construye un Builder pre-poblado con empresa, usuario, rol y timestamp.
     * El caller agrega filtros y totales con {@code .addFilter()} / {@code .addTotal()}
     * antes de llamar {@code .build()}.
     */
    public ReportHeaderBuilder.ReportContext.Builder baseContext(String reportTitle) {
        ReportHeaderBuilder.ReportContext.Builder b = ReportHeaderBuilder.ReportContext.builder()
                .reportTitle(reportTitle);

        // Empresa: preferir Company del tenant; fallback a SystemInfoService.
        Long companyId = TenantContext.getCompanyId();
        if (companyId != null) {
            try {
                Company c = companyRepository.findById(companyId).orElse(null);
                if (c != null) {
                    b.companyName(c.getBusinessName());
                    b.companyNit(c.getNit());
                }
            } catch (Exception ex) {
                // Defensive: tenant filter raro
            }
        }
        // Si no se pudo resolver desde Company, intentar SystemInfoService
        if (TenantContext.isPlatformAdmin() || companyId == null) {
            try {
                String name = systemInfoService.getCompanyName();
                String nit = systemInfoService.getCompanyNit();
                if (name != null) b.companyName(name);
                if (nit != null) b.companyNit(nit);
            } catch (Exception ignored) {}
        }

        // Usuario actual desde SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            List<String> roles = extractRoles(auth);
            String email = resolveUserEmail(username);
            b.userEmail(email != null ? email : username);
            b.roles(roles);
        }

        return b;
    }

    /**
     * Extrae el conjunto de roles legibles del Authentication. Filtra:
     * <ul>
     *   <li>Mantiene authorities con prefijo {@code ROLE_} (sin el prefijo)</li>
     *   <li>Mantiene {@code PLATFORM_ADMIN} sin prefijo</li>
     *   <li>Descarta granular permissions {@code PERM_*} y temporales {@code TEMP_*}</li>
     * </ul>
     */
    private List<String> extractRoles(Authentication auth) {
        List<String> roles = new ArrayList<>();
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String a = ga.getAuthority();
            if (a == null) continue;
            if (a.startsWith("ROLE_")) {
                roles.add(a.substring(5));
            } else if ("PLATFORM_ADMIN".equals(a)) {
                roles.add("PLATFORM_ADMIN");
            }
            // PERM_* y TEMP_* se descartan para no ensuciar el header
        }
        return roles.stream().distinct().collect(Collectors.toList());
    }

    private String resolveUserEmail(String username) {
        if (username == null || username.isBlank()) return null;
        try {
            // username puede ser email o username; intentar ambos
            return userRepository.findByUsernameOrEmail(username, username)
                    .map(User::getEmail)
                    .orElse(username);
        } catch (Exception ex) {
            return username;
        }
    }
}
