package com.sigcon.backend.general.accounting;

/**
 * Estados del periodo contable.
 * OPEN: Permite registrar operaciones.
 * CLOSED: CG cerrado, modulos bloqueados. Puede reabrirse con autorizacion.
 * LOCKED: Bloqueo permanente, solo lectura.
 */
public enum AccountingPeriodStatus {
    OPEN, CLOSED, LOCKED
}
