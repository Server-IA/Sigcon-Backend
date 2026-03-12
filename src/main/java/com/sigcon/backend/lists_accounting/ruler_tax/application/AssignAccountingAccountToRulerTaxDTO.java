package com.sigcon.backend.lists_accounting.ruler_tax.application;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssignAccountingAccountToRulerTaxDTO {
    
    @NotEmpty(message = "La lista de cuentas contables es requerida")
    private List<Long> accountingAccountIds;

    @NotNull(message = "El ID de la regla de impuesto es requerido")
    private Long rulerTaxId;

}
