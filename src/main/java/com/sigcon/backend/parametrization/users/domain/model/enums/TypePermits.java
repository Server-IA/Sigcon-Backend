package com.sigcon.backend.parametrization.users.domain.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TypePermits {

    CREATE,
    READ,
    UPDATE,
    DELETE;

    @JsonCreator
    public static TypePermits from(String value) {

        if (value == null || value.trim().isEmpty()) {
            return null; // @NotNull lo validará en el DTO
        }

        try {
            return TypePermits.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El tipo de permiso no es válido.");
        }
    }
}
