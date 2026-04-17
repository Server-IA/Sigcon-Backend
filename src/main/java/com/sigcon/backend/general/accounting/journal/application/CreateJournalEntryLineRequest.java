package com.sigcon.backend.general.accounting.journal.application;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para crear una linea de detalle dentro de un asiento contable.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateJournalEntryLineRequest {

    @NotNull(message = "La cuenta contable es obligatoria.")
    private Long accountingAccountId;

    @NotNull(message = "El monto debito es obligatorio.")
    private BigDecimal debitAmount;

    @NotNull(message = "El monto credito es obligatorio.")
    private BigDecimal creditAmount;

    private String description;

    private String thirdPartyNit;

    private Long costCenterId;
}
