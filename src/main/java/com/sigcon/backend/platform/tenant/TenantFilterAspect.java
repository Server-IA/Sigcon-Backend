package com.sigcon.backend.platform.tenant;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Aspecto que habilita el {@code @Filter("tenantFilter")} de Hibernate antes
 * de cualquier invocacion a un metodo publico de un bean {@code @Repository}.
 *
 * <p>Why este approach en vez del {@link TenantFilterActivatorInterceptor}:
 * Spring Data JPA crea una sub-sesion transaccional cuando ejecuta queries
 * de {@code JpaSpecificationExecutor.findAll(Specification)}. Esa sub-sesion
 * NO hereda los filters habilitados en el EntityManager del OSIV. Habilitar
 * el filter en un {@code HandlerInterceptor} (pre-request) no sirve porque
 * al momento de ejecutar la query ya estamos en otra sesion.
 *
 * <p>Con este aspect, el filter se habilita *inmediatamente antes* de cada
 * llamada a repository, en la sesion transaccional ya activa. Garantiza que
 * todas las queries sobre entidades tenant-scoped filtren por company_id.
 *
 * <p>Bypass para PLATFORM_ADMIN: se deshabilita explicitamente por si quedo
 * habilitado de una invocacion previa en el mismo thread.
 */
@Slf4j
@Aspect
@Component
public class TenantFilterAspect {

    public static final String FILTER_NAME = "tenantFilter";
    public static final String PARAM_TENANT_ID = "tenantId";

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* org.springframework.data.jpa.repository.JpaRepository+.*(..))")
    public void enableFilter(JoinPoint joinPoint) {
        try {
            Session session = entityManager.unwrap(Session.class);

            if (TenantContext.isPlatformAdmin()) {
                session.disableFilter(FILTER_NAME);
                return;
            }

            Long cid = TenantContext.getCompanyId();
            if (cid != null) {
                // Si el filter ya esta habilitado con el mismo tenant, no se toca.
                org.hibernate.Filter existing = session.getEnabledFilter(FILTER_NAME);
                if (existing == null) {
                    existing = session.enableFilter(FILTER_NAME);
                }
                existing.setParameter(PARAM_TENANT_ID, cid);
            } else {
                // Sin tenant (request anonimo o sin JWT): deshabilitar por si
                // quedo habilitado. La seguridad real esta en SecurityConfig.
                session.disableFilter(FILTER_NAME);
            }
        } catch (Exception e) {
            log.debug("TenantFilterAspect: no se pudo habilitar filter en {}: {}",
                    joinPoint.getSignature().toShortString(), e.getMessage());
        }
    }
}
