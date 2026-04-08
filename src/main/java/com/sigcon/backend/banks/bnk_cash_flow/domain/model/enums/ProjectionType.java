package com.sigcon.backend.banks.bnk_cash_flow.domain.model.enums;

/**
 * BNK-RF-29 — Tipo de proyección de flujo de caja.
 *
 * INGRESOS : Solo entradas de efectivo.
 * EGRESOS  : Solo salidas de efectivo.
 * NETA     : Diferencia entre ingresos y egresos.
 */
public enum ProjectionType {
    INGRESOS,
    EGRESOS,
    NETA
}
