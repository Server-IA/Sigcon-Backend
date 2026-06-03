package com.sigcon.backend.banks.cash_audits.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para modificar un arqueo de caja en estado BORRADOR o RECHAZADO
 * (BNK-HU-030: "Registrar y modificar arqueos de caja").
 * <p>
 * La caja del arqueo NO es editable (es la referencia original); solo se
 * permiten cambiar la fecha del arqueo, el saldo fisico contado y las notas.
 * El saldo del sistema y la diferencia se recalculan automaticamente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCashAuditRequest {

    @NotNull(message = "La fecha del arqueo es obligatoria.")
    private LocalDate auditDate;

    @NotNull(message = "El saldo fisico contado es obligatorio.")
    private BigDecimal physicalBalance;

    private String notes;
}
