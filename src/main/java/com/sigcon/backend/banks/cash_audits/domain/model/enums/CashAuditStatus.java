package com.sigcon.backend.banks.cash_audits.domain.model.enums;

/**
 * BNK-HU-046 a BNK-HU-048 - Estados validos de un arqueo de caja.
 *
 * Ciclo de vida (HU-BNK-047 E4):
 *   BORRADOR (cajero en ejecucion) -> EN_REVISION (enviado a supervisor)
 *     -> APROBADO (inmutable) | RECHAZADO (vuelve a BORRADOR)
 *
 * Operaciones excepcionales (HU-BNK-048):
 *   BORRADOR -> eliminacion fisica (con motivo).
 *   APROBADO -> ANULADO (soft delete, conserva historial, motivo min 50 chars).
 *
 * ABIERTO/CERRADO se conservan por compatibilidad con datos creados antes de
 * la migracion BORRADOR. ABIERTO se trata como sinonimo funcional de BORRADOR.
 */
public enum CashAuditStatus {
    BORRADOR,
    EN_REVISION,
    APROBADO,
    RECHAZADO,
    ANULADO,
    /** @deprecated usar BORRADOR. Conservado para datos historicos. */
    @Deprecated
    ABIERTO,
    /** @deprecated usar APROBADO. Conservado para datos historicos. */
    @Deprecated
    CERRADO
}
