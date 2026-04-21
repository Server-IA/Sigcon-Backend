package com.sigcon.backend.platform.tenant;

/**
 * Contexto de tenant (empresa) resuelto para la peticion HTTP actual.
 *
 * <p>Almacena el {@code companyId} y un flag {@code platformAdmin} que indica
 * si el usuario autenticado es un administrador de la plataforma
 * (cross-empresa, con bypass del filtro multi-tenant).
 *
 * <p>Thread-local: se setea al inicio del request por {@link TenantContextFilter}
 * leyendo el JWT, y se limpia obligatoriamente al final (finally). Si el valor
 * quedara sin limpiar, el siguiente request procesado por el mismo thread del
 * pool de Tomcat heredaria el tenant equivocado.
 *
 * <p>Lectura desde cualquier servicio:
 * <pre>
 *   Long cid = TenantContext.getCompanyId();            // o null si PLATFORM_ADMIN
 *   boolean bypass = TenantContext.isPlatformAdmin();   // true si hay bypass
 * </pre>
 *
 * <p>Para codigo que se ejecuta fuera del request cycle (p. ej. flujos
 * {@code @Async} o {@code @Scheduled}), hay que propagar manualmente el
 * tenant via {@link #runAs(Long, boolean, Runnable)} o el helper de
 * {@link TenantContextHolder}.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> COMPANY_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PLATFORM_ADMIN = ThreadLocal.withInitial(() -> false);

    private TenantContext() {}

    public static void setCompanyId(Long companyId) {
        COMPANY_ID.set(companyId);
    }

    public static Long getCompanyId() {
        return COMPANY_ID.get();
    }

    public static void setPlatformAdmin(boolean platformAdmin) {
        PLATFORM_ADMIN.set(platformAdmin);
    }

    public static boolean isPlatformAdmin() {
        Boolean v = PLATFORM_ADMIN.get();
        return v != null && v;
    }

    /** Limpia ambos thread-locals. Llamar obligatoriamente en finally. */
    public static void clear() {
        COMPANY_ID.remove();
        PLATFORM_ADMIN.remove();
    }

    /**
     * Ejecuta un runnable con un tenant context temporal. Util para flujos
     * asincronos donde hay que propagar el tenant del hilo original.
     */
    public static void runAs(Long companyId, boolean platformAdmin, Runnable runnable) {
        Long previousCompanyId = COMPANY_ID.get();
        boolean previousPlatformAdmin = isPlatformAdmin();
        try {
            COMPANY_ID.set(companyId);
            PLATFORM_ADMIN.set(platformAdmin);
            runnable.run();
        } finally {
            COMPANY_ID.set(previousCompanyId);
            PLATFORM_ADMIN.set(previousPlatformAdmin);
        }
    }
}
