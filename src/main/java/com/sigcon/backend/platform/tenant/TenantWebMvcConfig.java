package com.sigcon.backend.platform.tenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * Registra {@link TenantFilterActivatorInterceptor} en la cadena de
 * HandlerInterceptors para que se ejecute DESPUES del
 * OpenEntityManagerInViewInterceptor de Spring Boot. Con eso, al correr el
 * preHandle ya hay sesion JPA abierta y podemos habilitar el filter de
 * Hibernate.
 *
 * <p>Excluimos las rutas publicas (auth, health, swagger) para evitar overhead
 * en requests que no necesitan tenant filtering. Endpoints /api/platform/**
 * se incluyen pero el interceptor detecta {@code isPlatformAdmin} y hace
 * bypass automaticamente.
 */
@Configuration
@RequiredArgsConstructor
public class TenantWebMvcConfig implements WebMvcConfigurer {

    private final TenantFilterActivatorInterceptor tenantFilterActivator;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantFilterActivator)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/contabilidad/health",
                        "/**/api/contabilidad/health"
                );
    }
}
