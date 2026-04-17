package com.sigcon.backend.third_parties.bank_accounts.application;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para vincular una cuenta bancaria a un tercero.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LinkBankAccountRequest {

    @NotNull(message = "El ID de la cuenta bancaria es obligatorio")
    private Long bankAccountId;

    /** Indica si la cuenta debe ser marcada como principal */
    private Boolean isPrimary;
}
