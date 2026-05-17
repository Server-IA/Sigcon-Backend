package com.sigcon.backend.parametrization.users.domain.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TypePermits {

    CREATE,
    READ,
    UPDATE,
    DELETE,
    /**
     * QA Bloque AX-bis (2026-05-17): alias de READ. El frontend en 13 archivos
     * hace checks p.type === 'VIEW' (heredado de un nombre de tipo antiguo).
     * En BD seguimos guardando READ; al exponer al frontend via getUserInfo
     * mapeamos READ -> VIEW. Tener VIEW como valor del enum permite usarlo
     * sin lanzar IllegalArgumentException al serializar.
     */
    VIEW,
    /** Idem para acciones tipo "asignar permisos" (Bug visto en Bancos). */
    ASSIGN;

    @JsonCreator
    public static TypePermits from(String value) {

        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            String norm = value.trim().toUpperCase();
            // Tolerar 'VIEW' como sinonimo de READ y viceversa.
            if ("VIEW".equals(norm)) return VIEW;
            if ("READ".equals(norm)) return READ;
            return TypePermits.valueOf(norm);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El tipo de permiso no es válido.");
        }
    }
}
