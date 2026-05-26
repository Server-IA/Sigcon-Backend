package com.sigcon.backend.banks.financialmovements.application;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchVoucherRequest {
    private Long voucherId;
    private Long bankAccountId;

    /**
     * QA Conciliación (2026-05-25) Bug 7: motivo obligatorio (mínimo 10 caracteres) al
     * emparejar un movimiento con un comprobante/asiento contable desde el modal de
     * conciliación, igual que en el emparejamiento manual del Paso 6.
     */
    private String motivo;
}
