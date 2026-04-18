package com.sigcon.backend.banks.cash_audits.domain.model.enums;

/**
 * BNK-RF-17 a BNK-RF-20 - Estados validos de un arqueo de caja.
 *
 * ABIERTO     : Estado inicial tras el conteo fisico.
 * EN_REVISION : En proceso de revision por supervisor.
 * APROBADO    : Aprobado, diferencia registrada contablemente.
 * CERRADO     : Proceso finalizado e inmutable.
 */
public enum CashAuditStatus {
    ABIERTO,
    EN_REVISION,
    APROBADO,
    CERRADO
}
