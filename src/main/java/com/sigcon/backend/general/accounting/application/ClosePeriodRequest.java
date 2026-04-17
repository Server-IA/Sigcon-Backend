package com.sigcon.backend.general.accounting.application;

import lombok.*;

/**
 * Peticion para cerrar un periodo contable.
 * Permite incluir notas opcionales sobre el motivo del cierre.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class ClosePeriodRequest {
    private String notes;
}
