package com.sigcon.backend.banks.cash_audits.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para aprobar un arqueo de caja.
 * El campo notes es opcional y permite registrar observaciones del supervisor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveCashAuditRequest {

    private String notes;
}
