package com.sigcon.backend.bnk_cash_flow.domain.model.enums;

/**
 * BNK-RF-31 — Estados válidos de una proyección de flujo de caja.
 *
 * BORRADOR  : Estado inicial. Permite edición libre.
 * APROBADA  : Requiere motivo para modificar campos sensibles.
 * EJECUTADA : Proyección en ejecución. Solo lectura.
 * INACTIVA  : Inactivada lógicamente. No se puede editar ni ejecutar.
 */
public enum ProjectionStatus {
    BORRADOR,
    APROBADA,
    EJECUTADA,
    INACTIVA
}
