package com.sigcon.backend.platform.tenant;

/**
 * Se lanza cuando un usuario intenta acceder a un recurso que pertenece
 * a otra empresa. El GlobalExceptionHandler la traduce a HTTP 404 (no 403)
 * para no revelar la existencia del recurso (ver HU-TENANT-01, E-MT-TENANT).
 */
public class TenantIsolationException extends RuntimeException {
    public TenantIsolationException(String message) {
        super(message);
    }
}
