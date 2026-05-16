-- ============================================================================
-- V9-ZZZZH : Bug fix raiz - empresas nuevas no reciben cuentas PPE (15xx)
-- Fecha: 2026-05-16
--
-- BUG IDENTIFICADO (Bloque AY):
-- DataInitializer ejecuta scripts en orden lexical:
--   V10-D, V10-G, V10-H ... V9-Z ... V9-ZZZZG
-- ('V1' < 'V9' caracter por caracter, asi que V10-* corre ANTES de V9-*)
--
-- V10-H redefine la funcion _tenant_auto_provision INCLUYENDO el seed de
-- cuentas PPE (clase 15xx) via _seed_ppe_accounts_for_tenant.
--
-- PERO V9-Z__multi_tenant_final_fixes.sql corre DESPUES (porque 'V9-Z' >
-- 'V10-H' lexicalmente: 'V1' vs 'V9' → '9' > '1'), y vuelve a redefinir
-- _tenant_auto_provision SIN PPE seed. Resultado: cualquier empresa creada
-- via API (CompanyService.createWithAdmin) ejecuta la version SIN PPE,
-- quedando sin cuentas 15xx.
--
-- SINTOMA reportado por usuario QA:
-- Empresa nueva → modal "Crear Regla de Depreciacion" → dropdown "Cuenta
-- contable asociada" muestra "No se encontraron resultados".
--
-- FIX (esta migracion):
--   1. Redefinir _tenant_auto_provision INCLUYENDO los 4 pasos extra:
--      - _seed_payroll_concepts_for_tenant  (16 conceptos NOM, de V10-G)
--      - _seed_parameters_for_tenant        (20 parameters, de V9-Z)
--      - _seed_ppe_accounts_for_tenant      (8 cuentas PPE 15xx, de V10-H)
--   2. Re-ejecutar PPE seed para TODAS las empresas existentes que no
--      tengan las 8 cuentas. Idempotente con NOT EXISTS.
--
-- Esta migracion corre lexicalmente DESPUES de V9-Z y V9-ZZ*, por lo que
-- es la "definicion final" de _tenant_auto_provision. Cualquier migracion
-- futura que la redefina debe incluir todos los seeds.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Validar pre-requisitos
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_proc WHERE proname = '_seed_ppe_accounts_for_tenant') THEN
        RAISE EXCEPTION 'V9-ZZZZH abort: _seed_ppe_accounts_for_tenant no existe. V10-H debe correr antes.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_proc WHERE proname = '_seed_payroll_concepts_for_tenant') THEN
        RAISE EXCEPTION 'V9-ZZZZH abort: _seed_payroll_concepts_for_tenant no existe. V10-G debe correr antes.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_proc WHERE proname = '_seed_parameters_for_tenant') THEN
        RAISE EXCEPTION 'V9-ZZZZH abort: _seed_parameters_for_tenant no existe. V9-Z debe correr antes.';
    END IF;
END $$;

-- ----------------------------------------------------------------------------
-- 2. Redefinir _tenant_auto_provision con TODOS los seeds en su orden
--    correcto. Esta es la version definitiva.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION _tenant_auto_provision(p_company_id BIGINT, p_year INT) RETURNS void AS $$
DECLARE
    v_month INT;
BEGIN
    -- 1. Periodos del anio
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

    -- 3. 19 mapeos contables default
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
    PERFORM _ensure_tenant_account_mapping(p_company_id, 'UTILIDAD_EJERCICIO', '3605', 'Utilidad del ejercicio', 'CREDIT');

    -- 4. 16 conceptos NOM legales (V10-G)
    PERFORM _seed_payroll_concepts_for_tenant(p_company_id);

    -- 5. 20 parameters COMPANY/AGROFUSION/sigcon (V9-Z)
    PERFORM _seed_parameters_for_tenant(p_company_id);

    -- 6. 8 cuentas PPE clase 15xx (V10-H) - CRITICO para Reglas de Depreciacion
    PERFORM _seed_ppe_accounts_for_tenant(p_company_id);
END;
$$ LANGUAGE plpgsql;

-- ----------------------------------------------------------------------------
-- 3. Re-ejecutar PPE seed para todas las empresas existentes que no las tengan
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    rec RECORD;
    ppe_count INT;
    repaired INT := 0;
BEGIN
    FOR rec IN SELECT id, business_name FROM companies WHERE deleted_at IS NULL LOOP
        SELECT COUNT(*) INTO ppe_count
          FROM accounting_accounts a
          JOIN cfg_chart_of_accounts puc ON puc.id = a.puc_id
         WHERE a.company_id = rec.id
           AND puc.account_code IN ('1504','1516','1520','1524','1528','1532','1540','1560')
           AND a.deleted_at IS NULL;

        IF ppe_count < 8 THEN
            PERFORM _seed_ppe_accounts_for_tenant(rec.id);
            repaired := repaired + 1;
            RAISE NOTICE '  Empresa id=% (%): PPE reparado (antes %, ahora 8)',
                rec.id, rec.business_name, ppe_count;
        END IF;
    END LOOP;
    RAISE NOTICE 'V9-ZZZZH: empresas reparadas con PPE: %', repaired;
END $$;
