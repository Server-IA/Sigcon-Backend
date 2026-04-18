package com.sigcon.backend.parametrization.account_mappings.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para exponer un mapeo concepto-cuenta por la API.
 * Incluye el codigo PUC y el nombre de la cuenta para evitar que el cliente
 * tenga que hacer un segundo request a accounting_accounts.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountMappingDTO {

    /** Identificador interno del mapeo. */
    private Long id;

    /** Codigo logico del concepto (ej. AR_CLIENTES). */
    private String conceptCode;

    /** Descripcion legible del concepto. */
    private String conceptDescription;

    /** Codigo PUC colombiano sugerido (ej. 1305). */
    private String pucCode;

    /** ID de la cuenta contable a la que apunta el mapeo. */
    private Long accountingAccountId;

    /** Nombre personalizado de la cuenta contable (accounting_accounts.custom_name). */
    private String accountingAccountName;
}
