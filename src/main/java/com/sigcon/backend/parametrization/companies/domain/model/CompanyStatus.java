package com.sigcon.backend.parametrization.companies.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Estado estándar para Company y CompanyLocation.
 * - Se persiste como texto (EnumType.STRING).
 * - Acepta aliases en ES/EN para compatibilidad de entrada.
 */
public enum CompanyStatus {
    ACTIVE,
    INACTIVE;

    @JsonCreator
    public static CompanyStatus fromJson(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase();
        return switch (v) {
            case "ACTIVE", "ACTIVO", "ACTIVADO", "ENABLED", "HABILITADO" -> ACTIVE;
            case "INACTIVE", "INACTIVO", "DESACTIVO", "DESACTIVADO", "DISABLED", "INHABILITADO" -> INACTIVE;
            default -> throw new IllegalArgumentException("Estado invalido. Use ACTIVE/INACTIVE (o ACTIVO/DESACTIVO).");
        };
    }

    @JsonValue
    public String toJson() {
        // Mantener formato estable en respuestas (y consistente con la persistencia)
        return this.name();
    }
}

