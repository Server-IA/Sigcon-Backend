/**
 * Infraestructura multi-tenant (V10-A, 2026-04-19).
 *
 * <p>Declara el {@code @FilterDef} global {@code tenantFilter} que se aplica a
 * todas las entidades tenant-scoped (con {@code @Filter(name="tenantFilter"...)}).
 * El filtro se habilita/deshabilita por request por
 * {@link com.sigcon.backend.platform.tenant.TenantFilterActivatorInterceptor}
 * segun el {@link com.sigcon.backend.platform.tenant.TenantContext} resuelto
 * del JWT.
 */
@FilterDef(
    name = "tenantFilter",
    parameters = @ParamDef(name = "tenantId", type = Long.class)
)
package com.sigcon.backend.platform.tenant;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
