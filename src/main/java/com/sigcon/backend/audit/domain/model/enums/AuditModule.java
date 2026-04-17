package com.sigcon.backend.audit.domain.model.enums;

/**
 * Modulos del sistema que pueden generar eventos de auditoria.
 *
 * <p>NOM (Nomina) fue restaurado al re-incorporarse el modulo standalone
 * con las HUs actualizadas (HU-NOM-01 a 06) del Excel oficial.
 */
public enum AuditModule {
    PA, TER, CFG, ACT, AP, AR, BNK, CG, NOM, INT, AU
}
