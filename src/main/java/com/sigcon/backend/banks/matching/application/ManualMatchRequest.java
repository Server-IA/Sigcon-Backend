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
}
