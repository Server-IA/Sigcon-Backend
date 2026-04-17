package com.sigcon.backend.general.accounting.closing.domain.model.enums;

/**
 * Tipo de cierre contable.
 * MONTHLY: cierre mensual de cuentas de resultado.
 * ANNUAL: cierre anual consolidado.
 * OPENING: asiento de apertura del nuevo periodo fiscal.
 */
public enum ClosingType {
    MONTHLY, ANNUAL, OPENING
}
