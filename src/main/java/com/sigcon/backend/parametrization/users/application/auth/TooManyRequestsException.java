package com.sigcon.backend.parametrization.users.application.auth;

/**
 * PA-RF-02 v3.0 (Control de Cambios PA, 2026-05-29): se lanza cuando el rate
 * limiter de recuperacion de contrasena fue excedido. El controller la mapea a
 * HTTP 429 (Too Many Requests).
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
