-- =============================================================================
-- V10-G: auto-provisionar conceptos de nomina por empresa (Bloque G fix)
-- Fecha: 2026-04-20
--
-- Bug detectado en barrido multi-tenant: V9-G solo sembro los 16 conceptos NOM
-- para la empresa id=1 (SIGCON DEMO). Al crear empresas nuevas no se replicaban,
-- bloqueando la liquidacion de nomina.
--
-- Solucion:
--   1. Extiende la funcion _tenant_auto_provision (V10-D) para clonar conceptos
--      NOM desde SIGCON DEMO a la nueva empresa.
--   2. Clona conceptos para empresas ya creadas (company_id > 1).
-- =============================================================================

-- Funcion helper: copia los 16 conceptos NOM de SIGCON DEMO al tenant destino.
-- Los accounting_account_debit_id / credit_id NO se copian porque son FK por
-- tenant (si existen en el seed de c1 podrian no existir en el otro tenant).
CREATE OR REPLACE FUNCTION _seed_payroll_concepts_for_tenant(p_company_id BIGINT) RETURNS void AS $$
BEGIN
    INSERT INTO payroll_concepts (
        company_id, code, name, concept_type, base_calculation,
        percentage, fixed_amount, formula_expression, legal_reference,
        status, created_at, updated_at
    )
    SELECT p_company_id, src.code, src.name, src.concept_type, src.base_calculation,
           src.percentage, src.fixed_amount, src.formula_expression, src.legal_reference,
           'ACTIVE', NOW(), NOW()
      FROM payroll_concepts src
     WHERE src.company_id = 1
       AND src.deleted_at IS NULL
       AND NOT EXISTS (
           SELECT 1 FROM payroll_concepts tgt
            WHERE tgt.company_id = p_company_id
              AND tgt.code = src.code
              AND tgt.deleted_at IS NULL
       );
END;
$$ LANGUAGE plpgsql;

-- Extender _tenant_auto_provision para incluir el seed NOM.
-- La funcion completa se redefine; mantiene periodos + cost center + mapeos +
-- ahora agrega conceptos NOM.
CREATE OR REPLACE FUNCTION _tenant_auto_provision(p_company_id BIGINT, p_year INT) RETURNS void AS $$
DECLARE
    v_month INT;
BEGIN
    -- 1. Periodos del ano
    FOR v_month IN 1..12 LOOP
        INSERT INTO accounting_periods (company_id, year, month, status, created_at, updated_at)
             SELECT p_company_id, p_year, v_month, 'OPEN', NOW(), NOW()
              WHERE NOT EXISTS (
                  SELECT 1 FROM accounting_periods
                   WHERE company_id = p_company_id AND year = p_year AND month = v_month
              );
    END LOOP;

    -- 2. Centro de costo default
    INSERT INTO cost_centers (company_id, code, name, description, status, created_at, updated_at)
         SELECT p_company_id, 'CC-DEFAULT', 'Centro de Costo Default',
                'Centro de costo por defecto auto-generado al crear la empresa', 'ACTIVE', NOW(), NOW()
          WHERE NOT EXISTS (
              SELECT 1 FROM cost_centers WHERE company_id = p_company_id AND code = 'CC-DEFAULT' AND deleted_at IS NULL
          );

    -- 3. 18 mapeos contables default
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'AR_CLIENTES', '1305', 'Clientes', 'DEBIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'AR_ANTICIPOS', '2805', 'Anticipos de clientes', 'CREDIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'AR_RET_PRACTICADAS_CLIENTE', '1355', 'Anticipo impuestos (ret practicadas)', 'DEBIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'AR_INGRESOS', '4135', 'Ingresos operacionales', 'CREDIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'AR_IVA_GENERADO', '2408', 'IVA generado', 'CREDIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'AP_PROVEEDORES', '2205', 'Proveedores nacionales', 'CREDIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'AP_ANTICIPOS', '1330', 'Anticipos a proveedores', 'DEBIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'AP_RET_PRACTICADAS', '2365', 'Retencion en la fuente', 'CREDIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'AP_IVA_DESCONTABLE', '2408', 'IVA descontable', 'CREDIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'AP_COMPRAS_DEFAULT', '5135', 'Gastos - servicios (default AAEF)', 'DEBIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'BANCOS_DEFAULT', '1110', 'Bancos', 'DEBIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'CAJA_DEFAULT', '1105', 'Caja', 'DEBIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'DIF_CAMBIO_INGRESO', '4215', 'Diferencia en cambio (ingreso)', 'CREDIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'DIF_CAMBIO_GASTO', '5305', 'Diferencia en cambio (gasto)', 'DEBIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'NOMINA_SALARIOS', '5105', 'Gastos de personal', 'DEBIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'NOMINA_CXP_EMPLEADOS', '2505', 'Salarios por pagar', 'CREDIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'NOMINA_RETENCIONES', '2370', 'Retenciones y aportes de nomina', 'CREDIT');
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'NOMINA_CESANTIAS', '2510', 'Cesantias consolidadas', 'CREDIT');
    -- CG cierre (bug fix post-Bloque G): utilidad/perdida del ejercicio para cuadrar el asiento
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'UTILIDAD_EJERCICIO', '3605', 'Utilidad del ejercicio', 'CREDIT');

    -- 4. 16 conceptos NOM legales (V10-G: seed por tenant)
    PERFORM _seed_payroll_concepts_for_tenant(p_company_id);
END;
$$ LANGUAGE plpgsql;

-- Re-ejecutar auto-provision para TODAS las empresas (incluyendo SIGCON DEMO).
-- Es idempotente y asegura que las empresas existentes reciban tanto los
-- conceptos NOM clonados como el nuevo mapeo UTILIDAD_EJERCICIO -> 3605
-- (bug fix del cierre mensual detectado en Bloque G smoke).
DO $$
DECLARE rec RECORD;
    cnt INT;
BEGIN
    SELECT COUNT(*) INTO cnt FROM companies WHERE deleted_at IS NULL;
    RAISE NOTICE 'V10-G: re-provision de % empresa(s)', cnt;
    FOR rec IN SELECT id FROM companies WHERE deleted_at IS NULL LOOP
        RAISE NOTICE 'V10-G: tenant_auto_provision para company_id=%', rec.id;
        PERFORM _tenant_auto_provision(rec.id, EXTRACT(YEAR FROM CURRENT_DATE)::INT);
    END LOOP;
END $$;

-- Garantia adicional: el mapping UTILIDAD_EJERCICIO debe existir en SIGCON DEMO
-- aunque el loop anterior haya tenido algun issue en orden de ejecucion.
SELECT _ensure_tenant_account_mapping(1, 'UTILIDAD_EJERCICIO', '3605', 'Utilidad del ejercicio', 'CREDIT');
