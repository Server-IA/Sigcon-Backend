package com.sigcon.backend.parametrization.account_mappings.domain.service;

/**
 * Constantes con los codigos de concepto contable utilizados en el sistema.
 *
 * <p>Mantener sincronizado con los registros seed de la migracion
 * {@code V31__account_mappings.sql}. Si se agrega un concepto nuevo aqui, tambien
 * debe agregarse en {@link AccountMappingService#REQUIRED_CONCEPTS} para que la
 * validacion fail-fast verifique su presencia al iniciar la aplicacion.
 *
 * <p>Estos codigos son usados desde los servicios que generan asientos contables
 * automaticos (AR, AP, BNK, etc.) para resolver la cuenta real sin hardcodear IDs.
 */
public final class AccountingConcept {

    private AccountingConcept() {}

    // ===== Cuentas por Cobrar (AR) =====
    /** Cuenta CxC clientes (PUC 1305). */
    public static final String AR_CLIENTES = "AR_CLIENTES";

    /** Anticipos recibidos de clientes (PUC 2805). */
    public static final String AR_ANTICIPOS = "AR_ANTICIPOS";

    /** Retenciones que los clientes nos practicaron (PUC 1355). */
    public static final String AR_RET_PRACTICADAS_CLIENTE = "AR_RET_PRACTICADAS_CLIENTE";

    /** Ingresos operacionales por ventas (PUC 4135). */
    public static final String AR_INGRESOS = "AR_INGRESOS";

    /** IVA generado en ventas (PUC 2408). */
    public static final String AR_IVA_GENERADO = "AR_IVA_GENERADO";

    // ===== Cuentas por Pagar (AP) =====
    /** Cuenta CxP proveedores (PUC 2205). */
    public static final String AP_PROVEEDORES = "AP_PROVEEDORES";

    /** Anticipos entregados a proveedores (PUC 1330). */
    public static final String AP_ANTICIPOS = "AP_ANTICIPOS";

    /** Retenciones que nosotros practicamos a terceros (PUC 2365). */
    public static final String AP_RET_PRACTICADAS = "AP_RET_PRACTICADAS";

    /** IVA descontable en compras (PUC 2408). */
    public static final String AP_IVA_DESCONTABLE = "AP_IVA_DESCONTABLE";

    // ===== Bancos y Caja =====
    /** Cuenta bancaria default si el movimiento no especifica banco (PUC 1110). */
    public static final String BANCOS_DEFAULT = "BANCOS_DEFAULT";

    /** Cuenta de caja default si el movimiento no especifica caja (PUC 1105). */
    public static final String CAJA_DEFAULT = "CAJA_DEFAULT";

    // ===== Diferencia en cambio =====
    /** Ingreso por diferencia en cambio (PUC 4215). */
    public static final String DIF_CAMBIO_INGRESO = "DIF_CAMBIO_INGRESO";

    /** Gasto por diferencia en cambio (PUC 5305). */
    public static final String DIF_CAMBIO_GASTO = "DIF_CAMBIO_GASTO";

    /**
     * Cuenta debito default para facturas de compra recibidas via AAEF (Type=02).
     * Usada cuando AgroFusion no detalla el tipo de gasto. Se mapea a PUC 5135
     * (Servicios) y el contador puede reclasificar manualmente si corresponde.
     * Requerido por HU-INT-RF-04 E2 / HU-AP-01 E5.
     */
    public static final String AP_COMPRAS_DEFAULT = "AP_COMPRAS_DEFAULT";

    // ─── NOM (Nomina) ──────────────────────────────────────────
    // Restaurados el 2026-04-16 al reincorporarse el modulo NOM standalone
    // con las HUs actualizadas del Excel oficial (HU-NOM-01 a 06).
    /** Gasto salarios (PUC 5105). Usado por JE de liquidacion de nomina. */
    public static final String NOMINA_SALARIOS = "NOMINA_SALARIOS";
    /** CxP empleados - neto a pagar (PUC 2505). */
    public static final String NOMINA_CXP_EMPLEADOS = "NOMINA_CXP_EMPLEADOS";
    /** Retenciones y aportes de nomina (PUC 2370). */
    public static final String NOMINA_RETENCIONES = "NOMINA_RETENCIONES";
    /** Cesantias consolidadas por pagar (PUC 2510). */
    public static final String NOMINA_CESANTIAS = "NOMINA_CESANTIAS";

    // ===== CG cierre mensual/anual =====
    /**
     * Cuenta de patrimonio para registrar utilidad/perdida del ejercicio (PUC 3605).
     * Usada por {@code ClosingService.buildClosingLines} para cuadrar el asiento de
     * cierre: si netResult &gt; 0 -> C 3605 (ganancia), si netResult &lt; 0 -> D 3605 (perdida).
     * Sin este mapeo el asiento de cierre queda desbalanceado y
     * {@code JournalEntryService.createEntry} lo rechaza.
     */
    public static final String UTILIDAD_EJERCICIO = "UTILIDAD_EJERCICIO";
}
