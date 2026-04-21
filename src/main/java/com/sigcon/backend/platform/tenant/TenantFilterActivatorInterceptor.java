package com.sigcon.backend.platform.tenant;

import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Habilita el {@code @Filter("tenantFilter")} de Hibernate en la sesion actual
 * con el {@code companyId} del {@link TenantContext}. Se ejecuta como
 * {@link HandlerInterceptor#preHandle} DESPUES del {@code OpenEntityManagerInView}
 * interceptor de Spring Boot, por lo que la sesion JPA ya esta abierta cuando
 * llega aqui.
 *
 * <p>Reglas:
 * <ul>
 *   <li>Si {@code TenantContext.isPlatformAdmin()}: NO se habilita el filtro.
 *       El PLATFORM_ADMIN ve datos cross-tenant (para listar usuarios globales,
 *       lotes AAEF cross, etc.).</li>
 *   <li>Si {@code TenantContext.getCompanyId() != null}: se habilita el filtro
 *       con ese tenantId. Todas las queries sobre entidades con
 *       {@code @Filter(name="tenantFilter")} filtran por {@code company_id}.</li>
 *   <li>Si no hay tenant ni platform admin (request anonimo a endpoint
 *       publico): no se toca. Cualquier query tenant-scoped sin filtro
 *       activo ni bypass se rechaza por el constraint NOT NULL de la
 *       FK company_id en BD (capa de defensa mas alla de este filtro).</li>
 * </ul>
 *
 * <p>La limpieza NO es necesaria: la sesion JPA se cierra al final del
 * request (OSIV), el filter no persiste.
 */
@Slf4j
@Component
public class TenantFilterActivatorInterceptor implements HandlerInterceptor {

    public static final String FILTER_NAME = "tenantFilter";
    public static final String PARAM_TENANT_ID = "tenantId";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean preHandle(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler) {
        try {
            Session session = entityManager.unwrap(Session.class);

            if (TenantContext.isPlatformAdmin()) {
                // Bypass: desactivar por si quedo habilitado de un request previo.
                session.disableFilter(FILTER_NAME);
                return true;
            }

            Long cid = TenantContext.getCompanyId();
            if (cid != null) {
                Filter f = session.enableFilter(FILTER_NAME);
                f.setParameter(PARAM_TENANT_ID, cid);
                log.info("tenantFilter ON companyId={}", cid);
            }
        } catch (Exception e) {
            log.error("TenantFilterActivator FAIL: {}", e.getMessage(), e);
        }
        return true;
    }
}
