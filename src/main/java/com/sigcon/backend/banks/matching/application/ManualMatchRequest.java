package com.sigcon.backend.banks.matching.application;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * BNK-HU-070: solicitud de emparejamiento manual / preview de selección.
 * extractoIds = movimientos del lado extracto (BANK_IMPORT);
 * librosIds = movimientos del lado libros (MANUAL).
 */
@Data
public class ManualMatchRequest {

    @NotNull(message = "La cuenta bancaria es obligatoria.")
    private Long bankAccountId;

    private List<Long> extractoIds;

    private List<Long> librosIds;

    /** Obligatorio (>=30 chars) para N:M o cuando hay diferencia tolerada (HU-070 E4/E5). */
    private String motivo;

    /**
     * QA Conciliación (2026-05-25) Bug 1/4: sesión a la que pertenece el emparejamiento.
     * Permite que el Paso 5 (Aceptar/Rechazar) liste SOLO los emparejamientos de la
     * sesión seleccionada y no los de toda la cuenta (residuos de otra sesión).
     */
    private Long reconciliationSessionId;
}
