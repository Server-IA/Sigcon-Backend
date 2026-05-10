package com.sigcon.backend.parametrization.users.domain.model.enums;

public enum Status {
    ACTIVE,
    INACTIVE,
    /**
     * QA Bloque PA Bug 15 (HU-PA-07 E3, 2026-05-09): estado intermedio para
     * usuarios bloqueados temporalmente (ej. tras 5 intentos fallidos de login,
     * sospecha de actividad indebida). El usuario NO puede loguearse pero sus
     * datos no se han desactivado. Visible en filtros del listado de usuarios.
     */
    BLOCKED
}
