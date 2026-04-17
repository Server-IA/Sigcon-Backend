package com.sigcon.backend.general.accounting.journal.domain.model.enums;

/**
 * Modulo origen del asiento contable.
 * AP: Cuentas por Pagar, AR: Cuentas por Cobrar, BNK: Bancos,
 * ACT: Activos, NOM: Nomina, CG: Contabilidad General.
 *
 * <p>NOTA: NOM fue eliminado el 2026-04-16 por confusion con el bloque payroll
 * del borrador AAEF (desestimado por el profesor). Se restauro el mismo dia
 * al recibir el Excel oficial actualizado con las 6 HUs de Nomina standalone
 * (HU-NOM-01 a 06). La integracion AAEF de payroll sigue descartada.
 */
public enum JournalSourceModule {
    AP, AR, BNK, ACT, NOM, CG
}
