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
import java.util.List;
import java.util.Map;
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

    /**
     * QA Bloque AW (Opcion B paridad legacy<->nuevo, 2026-05-15):
     * mapeo bidireccional entre codes legacy ingles (VIEW_USER) y codes
     * nuevo MOD.ENTIDAD.ACCION (PAR.USUARIOS.VER). Cuando un usuario tiene
     * un permiso de una forma, el filter le inyecta TAMBIEN su par
     * equivalente. Asi un @PreAuthorize('PERM_VIEW_USERS') y un
     * @PreAuthorize('PERM_PAR.USUARIOS.VER') ambos pasan, sin importar
     * cual code tiene el rol del usuario.
     *
     * <p>Cobertura: pares principales detectados por la auditoria 2026-05-15
     * (44 codes ROTOS en @PreAuthorize, 22 con match plural/singular, 22 sin
     * match directo que se resuelven via este mapeo y la migracion V9-ZZZZG).
     */
    private static final Map<String, List<String>> LEGACY_TO_NEW = Map.ofEntries(
            // Parametrizacion
            Map.entry("VIEW_USER",          List.of("PAR.USUARIOS.VER")),
            Map.entry("VIEW_USERS",         List.of("PAR.USUARIOS.VER")),
            Map.entry("CREATE_USER",        List.of("PAR.USUARIOS.CREAR")),
            Map.entry("CREATE_USERS",       List.of("PAR.USUARIOS.CREAR")),
            Map.entry("UPDATE_USER",        List.of("PAR.USUARIOS.EDITAR")),
            Map.entry("UPDATE_USERS",       List.of("PAR.USUARIOS.EDITAR")),
            Map.entry("DELETE_USER",        List.of("PAR.USUARIOS.DESACTIVAR")),
            Map.entry("DELETE_USERS",       List.of("PAR.USUARIOS.DESACTIVAR")),
            Map.entry("VIEW_ROLE",          List.of("PAR.ROLES.VER")),
            Map.entry("VIEW_ROLES",         List.of("PAR.ROLES.VER")),
            Map.entry("CREATE_ROLE",        List.of("PAR.ROLES.CREAR")),
            Map.entry("UPDATE_ROLE",        List.of("PAR.ROLES.EDITAR")),
            Map.entry("DELETE_ROLE",        List.of("PAR.ROLES.ELIMINAR")),
            Map.entry("VIEW_PERMISSION",    List.of("PAR.PERMISOS.VER")),
            Map.entry("VIEW_PERMISSIONS",   List.of("PAR.PERMISOS.VER")),
            Map.entry("CREATE_PERMISSION",  List.of("PAR.PERMISOS.CREAR")),
            Map.entry("UPDATE_PERMISSION",  List.of("PAR.PERMISOS.EDITAR")),
            Map.entry("DELETE_PERMISSION",  List.of("PAR.PERMISOS.ELIMINAR")),
            Map.entry("VIEW_MODULE",        List.of("PAR.MODULOS.VER")),
            Map.entry("VIEW_MODULES",       List.of("PAR.MODULOS.VER")),
            Map.entry("CREATE_MODULE",      List.of("PAR.MODULOS.CREAR")),
            Map.entry("UPDATE_MODULE",      List.of("PAR.MODULOS.EDITAR")),
            Map.entry("DELETE_MODULE",      List.of("PAR.MODULOS.ELIMINAR")),
            Map.entry("VIEW_MENU",          List.of("PAR.MENUS.VER")),
            Map.entry("VIEW_MENUS",         List.of("PAR.MENUS.VER")),
            Map.entry("CREATE_MENU",        List.of("PAR.MENUS.CREAR")),
            Map.entry("UPDATE_MENU",        List.of("PAR.MENUS.EDITAR")),
            Map.entry("DELETE_MENU",        List.of("PAR.MENUS.ELIMINAR")),
            // Typo historico - el code real es PAR.ROLES.EDITAR
            Map.entry("PARAMETRIZACION.ROLES.EDITAR", List.of("PAR.ROLES.EDITAR")),
            // Listas Contables
            Map.entry("VIEW_COST_CENTER",   List.of("CFG.CENTROS_COSTO.VER")),
            Map.entry("VIEW_COST_CENTERS",  List.of("CFG.CENTROS_COSTO.VER")),
            Map.entry("CREATE_COST_CENTER", List.of("CFG.CENTROS_COSTO.CREAR")),
            Map.entry("UPDATE_COST_CENTER", List.of("CFG.CENTROS_COSTO.EDITAR")),
            Map.entry("DELETE_COST_CENTER", List.of("CFG.CENTROS_COSTO.ELIMINAR")),
            // Terceros
            Map.entry("VIEW_THIRD_PARTY",   List.of("TER.TERCEROS.VER")),
            Map.entry("VIEW_THIRD_PARTIES", List.of("TER.TERCEROS.VER")),
            Map.entry("CREATE_THIRD_PARTY", List.of("TER.TERCEROS.CREAR")),
            Map.entry("CREATE_THIRD_PARTIES", List.of("TER.TERCEROS.CREAR")),
            Map.entry("UPDATE_THIRD_PARTY", List.of("TER.TERCEROS.EDITAR")),
            Map.entry("MANAGE_THIRD_PARTY_ROLES_STATUS", List.of("TER.TERCEROS.EDITAR")),
            Map.entry("DELETE_THIRD_PARTY", List.of("TER.TERCEROS.DAR_DE_BAJA")),
            Map.entry("DELETE_THIRD_PARTIES", List.of("TER.TERCEROS.DAR_DE_BAJA")),
            Map.entry("VIEW_ECL_SEGMENT",   List.of("TER.SEGMENTACION.VER")),
            // Bancos y Cajas
            Map.entry("VIEW_BANK",          List.of("BNK.BANCOS.VER")),
            Map.entry("CREATE_BANK",        List.of("BNK.BANCOS.CREAR")),
            Map.entry("UPDATE_BANK",        List.of("BNK.BANCOS.EDITAR")),
            Map.entry("DELETE_BANK",        List.of("BNK.BANCOS.ELIMINAR")),
            Map.entry("DELETE_BANK_CHECK",  List.of("BNK.CHEQUES.ELIMINAR")),
            Map.entry("VOID_BANK_CHECK",    List.of("BNK.CHEQUES.ANULAR")),
            Map.entry("RECONCILE_BANK_CHECK", List.of("BNK.CHEQUES.CONCILIAR")),
            Map.entry("REPORT_LOST_BANK_CHECK", List.of("BNK.CHEQUES.REPORTAR_PERDIDO")),
            // Cuentas por Cobrar
            Map.entry("VIEW_SALES_INVOICE", List.of("AR.FACTURAS_VENTA.VER")),
            Map.entry("READ_SALES_INVOICE", List.of("AR.FACTURAS_VENTA.VER")),
            Map.entry("CREATE_SALES_INVOICE", List.of("AR.FACTURAS_VENTA.CREAR")),
            Map.entry("UPDATE_SALES_INVOICE", List.of("AR.FACTURAS_VENTA.EDITAR")),
            Map.entry("DELETE_SALES_INVOICE", List.of("AR.FACTURAS_VENTA.ANULAR")),
            Map.entry("READ_AR_PAYMENT",    List.of("AR.COBROS.VER")),
            Map.entry("CREATE_AR_PAYMENT",  List.of("AR.COBROS.CREAR")),
            Map.entry("READ_AR_ADVANCE",    List.of("AR.ANTICIPOS.VER")),
            Map.entry("CREATE_AR_ADVANCE",  List.of("AR.ANTICIPOS.CREAR")),
            Map.entry("READ_AR_NOTE",       List.of("AR.NOTAS.VER")),
            Map.entry("CREATE_AR_NOTE",     List.of("AR.NOTAS.CREAR")),
            Map.entry("VIEW_DIAN_RESOLUTION", List.of("AR.RESOLUCIONES_DIAN.VER")),
            Map.entry("READ_DIAN_RESOLUTION", List.of("AR.RESOLUCIONES_DIAN.VER")),
            Map.entry("CREATE_DIAN_RESOLUTION", List.of("AR.RESOLUCIONES_DIAN.CREAR")),
            Map.entry("UPDATE_DIAN_RESOLUTION", List.of("AR.RESOLUCIONES_DIAN.EDITAR")),
            Map.entry("DELETE_DIAN_RESOLUTION", List.of("AR.RESOLUCIONES_DIAN.ELIMINAR")),
            Map.entry("READ_DIAN",          List.of("AR.DIAN.GENERAR")),
            Map.entry("READ_DIAN_REPORT",   List.of("AR.DIAN.GENERAR")),
            Map.entry("CREATE_DIAN_XML",    List.of("AR.DIAN.GENERAR")),
            Map.entry("SUBMIT_DIAN",        List.of("AR.DIAN.GENERAR")),
            // Contabilidad General
            Map.entry("VIEW_ACCOUNTING",    List.of("CG.LIBROS.VER")),
            Map.entry("VIEW_TAX_REPORT",    List.of("CG.REPORTES.VER")),
            // Reportes y plantillas
            Map.entry("VIEW_REPORT_TYPE",       List.of("PAR.REPORTES_TIPOS.VER")),
            Map.entry("VIEW_REPORT_TYPES",      List.of("PAR.REPORTES_TIPOS.VER")),
            Map.entry("CREATE_REPORT_TYPE",     List.of("PAR.REPORTES_TIPOS.CREAR")),
            Map.entry("CREATE_REPORT_TYPES",    List.of("PAR.REPORTES_TIPOS.CREAR")),
            Map.entry("UPDATE_REPORT_TYPE",     List.of("PAR.REPORTES_TIPOS.EDITAR")),
            Map.entry("UPDATE_REPORT_TYPES",    List.of("PAR.REPORTES_TIPOS.EDITAR")),
            Map.entry("DELETE_REPORT_TYPE",     List.of("PAR.REPORTES_TIPOS.ELIMINAR")),
            Map.entry("DELETE_REPORT_TYPES",    List.of("PAR.REPORTES_TIPOS.ELIMINAR")),
            Map.entry("VIEW_REPORT_TEMPLATE",   List.of("PAR.REPORTES_PLANTILLAS.VER")),
            Map.entry("VIEW_REPORT_TEMPLATES",  List.of("PAR.REPORTES_PLANTILLAS.VER")),
            Map.entry("CREATE_REPORT_TEMPLATE", List.of("PAR.REPORTES_PLANTILLAS.GESTIONAR")),
            Map.entry("CREATE_REPORT_TEMPLATES",List.of("PAR.REPORTES_PLANTILLAS.GESTIONAR")),
            Map.entry("DELETE_REPORT_TEMPLATE", List.of("PAR.REPORTES_PLANTILLAS.GESTIONAR")),
            Map.entry("DELETE_REPORT_TEMPLATES",List.of("PAR.REPORTES_PLANTILLAS.GESTIONAR"))
    );

    /** Indice inverso nuevo->legacy, construido al cargar la clase. */
    private static final Map<String, List<String>> NEW_TO_LEGACY = buildReverseIndex(LEGACY_TO_NEW);

    private static Map<String, List<String>> buildReverseIndex(Map<String, List<String>> direct) {
        java.util.HashMap<String, List<String>> reverse = new java.util.HashMap<>();
        for (Map.Entry<String, List<String>> e : direct.entrySet()) {
            for (String newCode : e.getValue()) {
                reverse.computeIfAbsent(newCode, k -> new java.util.ArrayList<>()).add(e.getKey());
            }
        }
        return reverse;
    }

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
                        addRoleAuthorityWithVariants(result, p.getCode());
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
                    addTempAuthorityWithVariants(result, code);
                }
            }
        } catch (Exception ex) {
            log.debug("EffectivePermissionsFilter: error computing temporal codes for userId={}: {}",
                    user.getId(), ex.getMessage());
        }

        return result;
    }

    /**
     * QA Bloque AV (HU-PA-12 E4 + cobertura plural/singular, 2026-05-14):
     * agrega la authority del rol y sus variantes plural/singular para
     * tolerar el desfase historico entre permisos en BD y @PreAuthorize.
     *
     * <p>Ejemplo: el permiso en BD es {@code VIEW_USER} pero varios
     * controllers usan {@code @PreAuthorize("PERM_VIEW_USERS")} (plural).
     * Agregar AMBAS variantes garantiza que el endpoint funcione
     * independientemente de cual eligio el dev.
     *
     * <p>Tambien tolera codes que vienen ya con prefijo {@code PERM_}.
     */
    private void addRoleAuthorityWithVariants(Set<GrantedAuthority> result, String code) {
        String base = code.startsWith(ROLE_PERM_PREFIX) ? code.substring(ROLE_PERM_PREFIX.length()) : code;
        result.add(new SimpleGrantedAuthority(ROLE_PERM_PREFIX + base));
        // Agregar variante plural si no termina en S. Util cuando el code
        // singular en BD se uso plural en @PreAuthorize.
        if (!base.endsWith("S")) {
            result.add(new SimpleGrantedAuthority(ROLE_PERM_PREFIX + base + "S"));
        }
        // Agregar variante singular si termina en S (no si termina en SS).
        if (base.endsWith("S") && !base.endsWith("SS") && base.length() > 1) {
            String singular = base.substring(0, base.length() - 1);
            result.add(new SimpleGrantedAuthority(ROLE_PERM_PREFIX + singular));
        }
        // QA Bloque AW (Opcion B paridad legacy<->nuevo, 2026-05-15): por cada
        // code, inyectar tambien sus pares legacy<->nuevo del mapping global.
        // Asi un rol con VIEW_USER tambien recibe PERM_PAR.USUARIOS.VER y
        // viceversa.
        List<String> mappedNew = LEGACY_TO_NEW.get(base);
        if (mappedNew != null) {
            for (String newCode : mappedNew) {
                result.add(new SimpleGrantedAuthority(ROLE_PERM_PREFIX + newCode));
            }
        }
        List<String> mappedLegacy = NEW_TO_LEGACY.get(base);
        if (mappedLegacy != null) {
            for (String legacyCode : mappedLegacy) {
                result.add(new SimpleGrantedAuthority(ROLE_PERM_PREFIX + legacyCode));
            }
        }
    }

    /**
     * Misma logica que {@link #addRoleAuthorityWithVariants} pero usando el
     * prefijo {@code TEMP_}. Para permisos temporales se generan 4 variantes:
     * TEMP_PERM_X, TEMP_X (sin PERM_), TEMP_PERM_XS y TEMP_XS (plural).
     */
    private void addTempAuthorityWithVariants(Set<GrantedAuthority> result, String code) {
        String base = code.startsWith(ROLE_PERM_PREFIX) ? code.substring(ROLE_PERM_PREFIX.length()) : code;
        // Forma con PERM_ y sin PERM_ (TEMP_PERM_X y TEMP_X)
        result.add(new SimpleGrantedAuthority(TEMP_PREFIX + ROLE_PERM_PREFIX + base));
        result.add(new SimpleGrantedAuthority(TEMP_PREFIX + base));
        if (!base.endsWith("S")) {
            result.add(new SimpleGrantedAuthority(TEMP_PREFIX + ROLE_PERM_PREFIX + base + "S"));
            result.add(new SimpleGrantedAuthority(TEMP_PREFIX + base + "S"));
        }
        if (base.endsWith("S") && !base.endsWith("SS") && base.length() > 1) {
            String singular = base.substring(0, base.length() - 1);
            result.add(new SimpleGrantedAuthority(TEMP_PREFIX + ROLE_PERM_PREFIX + singular));
            result.add(new SimpleGrantedAuthority(TEMP_PREFIX + singular));
        }
        // QA Bloque AW (Opcion B): mismos pares legacy<->nuevo, prefijados con TEMP_
        List<String> mappedNew = LEGACY_TO_NEW.get(base);
        if (mappedNew != null) {
            for (String newCode : mappedNew) {
                result.add(new SimpleGrantedAuthority(TEMP_PREFIX + ROLE_PERM_PREFIX + newCode));
                result.add(new SimpleGrantedAuthority(TEMP_PREFIX + newCode));
            }
        }
        List<String> mappedLegacy = NEW_TO_LEGACY.get(base);
        if (mappedLegacy != null) {
            for (String legacyCode : mappedLegacy) {
                result.add(new SimpleGrantedAuthority(TEMP_PREFIX + ROLE_PERM_PREFIX + legacyCode));
                result.add(new SimpleGrantedAuthority(TEMP_PREFIX + legacyCode));
            }
        }
    }
}
