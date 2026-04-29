-- =============================================================================
-- V10-H: auto-provisionar cuentas PPE (clase 15xx) por empresa nueva.
-- Fecha: 2026-04-24
--
-- Bug detectado: V9-Z7 sembraba las 8 cuentas PPE (1504, 1516, 1520, 1524, 1528,
-- 1532, 1540, 1560) en TODAS las empresas activas — pero solo en arranque. Las
-- empresas creadas via API (CompanyService.create -> _tenant_auto_provision)
-- NO recibian estas cuentas, asi que el formulario "Crear Regla de Depreciacion"
-- mostraba "No se encontraron resultados" en el dropdown de Cuenta Contable
-- Asociada (la query filtra por pucAccount.code starting with 14|12|15|16).
--
-- Solucion:
--   1. Define funcion helper _seed_ppe_accounts_for_tenant(p_company_id) que
--      promueve las 8 cuentas PPE estandar desde cfg_chart_of_accounts a la
--      tabla accounting_accounts del tenant. Idempotente: skip si ya existe.
--   2. Extiende _tenant_auto_provision para llamar la funcion al crear empresa.
--   3. Re-ejecuta auto-provision para TODAS las empresas existentes.
-- =============================================================================

-- ------------------------------------------------------------------
-- 1. Funcion helper para sembrar PPE accounts en un tenant
-- ------------------------------------------------------------------
CREATE OR REPLACE FUNCTION _seed_ppe_accounts_for_tenant(p_company_id BIGINT) RETURNS void AS $$
DECLARE
    cop_id BIGINT;
BEGIN
    SELECT id INTO cop_id FROM cfg_currency_types
     WHERE iso_code = 'COP' AND deleted_at IS NULL LIMIT 1;
    IF cop_id IS NULL THEN
        RAISE NOTICE '_seed_ppe_accounts_for_tenant skipped: no cfg_currency_types COP found';
        RETURN;
    END IF;

    INSERT INTO accounting_accounts(company_id, custom_name, nature, status,
                                     currency_type_id, puc_id,
                                     created_at, updated_at)
    SELECT p_company_id,
           ca.account_name || ' (' || ca.account_code || ')',
           'DEBIT', 'ACTIVE', cop_id, ca.id, NOW(), NOW()
      FROM cfg_chart_of_accounts ca
     WHERE ca.deleted_at IS NULL
       AND ca.account_code IN ('1504','1516','1520','1524','1528','1532','1540','1560')
       AND NOT EXISTS (
           SELECT 1 FROM accounting_accounts a
            WHERE a.company_id = p_company_id
              AND a.puc_id = ca.id
              AND a.deleted_at IS NULL);
END;
$$ LANGUAGE plpgsql;

-- ------------------------------------------------------------------
-- 2. Re-definir _tenant_auto_provision incluyendo PPE seed
-- ------------------------------------------------------------------
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

    -- 3. 19 mapeos contables default (incluye UTILIDAD_EJERCICIO de V10-G)
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

    -- 5. 8 cuentas PPE clase 15xx (V10-H, este script)
    PERFORM _seed_ppe_accounts_for_tenant(p_company_id);
END;
$$ LANGUAGE plpgsql;

-- ------------------------------------------------------------------
-- 3. Re-ejecutar auto-provision para TODAS las empresas existentes
-- ------------------------------------------------------------------
DO $$
DECLARE rec RECORD;
    cnt INT;
BEGIN
    SELECT COUNT(*) INTO cnt FROM companies WHERE deleted_at IS NULL;
    RAISE NOTICE 'V10-H: re-provision PPE de % empresa(s)', cnt;
    FOR rec IN SELECT id FROM companies WHERE deleted_at IS NULL LOOP
        PERFORM _seed_ppe_accounts_for_tenant(rec.id);
    END LOOP;
END $$;
