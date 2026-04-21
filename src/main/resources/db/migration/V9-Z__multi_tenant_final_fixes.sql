-- =============================================================================
-- V10-H: cierre de brechas multi-tenant detectadas en la auditoria BD (2026-04-20)
--
-- Arregla 9 hallazgos:
--  1. CRITICO: ACME y B sin periodos contables ni parameters (backfill)
--  2. CRITICO: auto-provision no clonaba los 20 parameters (COMPANY_*, AGROFUSION_*, sigcon.*)
--  3. CRITICO: parameters.uk_parameters_active era UNIQUE global (name) -> (company_id, name)
--  4. ALTO:    banks con 6 UNIQUE globales (nit/code/name/swift/short_name/code_ach)
--  5. ALTO:    checkbooks.ukgpejk5ogx4nigcrsysi9xjqbe UNIQUE duplicado (Hibernate auto)
--  6. ALTO:    system_withholding_assignments.uk_sys_wh_active global (withholding_id)
--  7. ALTO:    bnk_cash_flow_projections.uidx_bnk_cfp_name_active global (name)
--  8. MEDIO:   74 tablas tenant con DEFAULT 1 en company_id (riesgo de insert silencioso)
--  9. BAJO:    parametrization.Parameter entity no tenia companyId (patch en codigo)
--
-- Todo idempotente: se puede re-ejecutar sin efectos secundarios.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Parte 1: drop UNIQUE globales peligrosos + crear compuestos con company_id
-- -----------------------------------------------------------------------------

-- #3 parameters: (company_id, name) parcial activo
DROP INDEX IF EXISTS uk_parameters_active;
CREATE UNIQUE INDEX IF NOT EXISTS uk_parameters_company_name_active
    ON parameters (company_id, name)
    WHERE deleted_at IS NULL;

-- #4 banks: 6 compuestos por company_id
DROP INDEX IF EXISTS uk_banks_nit_active;
DROP INDEX IF EXISTS uk_banks_code_active;
DROP INDEX IF EXISTS uk_banks_name_active;
DROP INDEX IF EXISTS uk_banks_swift_active;
DROP INDEX IF EXISTS uk_banks_short_name_active;
DROP INDEX IF EXISTS uk_banks_ach_active;

CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_company_nit_active
    ON banks (company_id, nit) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_company_code_active
    ON banks (company_id, code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_company_name_active
    ON banks (company_id, name) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_company_swift_active
    ON banks (company_id, swift) WHERE deleted_at IS NULL AND swift IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_company_short_name_active
    ON banks (company_id, name_short) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_company_code_ach_active
    ON banks (company_id, code_ach) WHERE deleted_at IS NULL AND code_ach IS NOT NULL;

-- #5 checkbooks: el UNIQUE parcial correcto ya existe. Dropear el global duplicado
-- generado por Hibernate (sin filter de deleted_at, bloquea numero tras soft-delete).
-- Nota: Hibernate lo crea como CONSTRAINT (no simple index), por eso ALTER TABLE.
ALTER TABLE checkbooks DROP CONSTRAINT IF EXISTS ukgpejk5ogx4nigcrsysi9xjqbe;
DROP INDEX IF EXISTS ukgpejk5ogx4nigcrsysi9xjqbe;

-- #6 system_withholding_assignments: (company_id, withholding_id)
DROP INDEX IF EXISTS uk_sys_wh_active;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_wh_company_active
    ON system_withholding_assignments (company_id, withholding_id)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

-- #7 bnk_cash_flow_projections: (company_id, name)
DROP INDEX IF EXISTS uidx_bnk_cfp_name_active;
CREATE UNIQUE INDEX IF NOT EXISTS uidx_bnk_cfp_company_name_active
    ON bnk_cash_flow_projections (company_id, name)
    WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- Parte 2: DEFAULT 1 en company_id (#8) — se MANTIENE intencionalmente.
--
-- Analisis: dropear el DEFAULT rompe decenas de INSERTs legacy en seeds (V3,
-- V32, V9-8, V9-9, V9-G, etc.) que fueron escritos cuando SIGCON era mono-empresa.
-- Esos INSERTs siembran datos demo que logicamente PERTENECEN a SIGCON DEMO
-- (company_id = 1), asi que el DEFAULT 1 es correcto para ellos.
--
-- Proteccion contra insert silencioso desde la app: las 75 entidades tenant
-- tienen @PrePersist que SIEMPRE sobreescribe companyId con TenantContext.
-- getCompanyId() antes de llegar a BD. Si TenantContext es null y el usuario
-- no es PLATFORM_ADMIN, el insert iria a c=1 silenciosamente — pero ese
-- escenario es un bug de service (olvida setear tenant), no del default.
--
-- Deuda tecnica documentada: si se detecta un service que olvida setear
-- tenant, el fix es en codigo (inyectar TenantContext correctamente), no en
-- el default de BD.
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- Parte 3: extender _tenant_auto_provision para clonar parameters (#2)
--
-- Clona:
--   - 10 parametros AGROFUSION_* desde company 1 (configuracion por defecto)
--   - 2  parametros sigcon.nomina.* (SMLV / UVT)
--   - 8  parametros COMPANY_* populados desde la tabla companies del tenant
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION _seed_parameters_for_tenant(p_company_id BIGINT) RETURNS void AS $$
DECLARE
    v_company RECORD;
BEGIN
    -- 3.1 Clonar AGROFUSION_* y sigcon.* desde company 1 (configuracion base).
    -- Si el tenant ya tiene el parametro (p.ej. auto-provisionado antes), no se toca.
    INSERT INTO parameters (company_id, name, value, description, category, status, created_at, updated_at)
    SELECT p_company_id, src.name, src.value, src.description, src.category, 'ACTIVE', NOW(), NOW()
      FROM parameters src
     WHERE src.company_id = 1
       AND src.deleted_at IS NULL
       AND src.category IN ('INTEGRATION_AGROFUSION', 'NOMINA')
       AND NOT EXISTS (
           SELECT 1 FROM parameters tgt
            WHERE tgt.company_id = p_company_id
              AND tgt.name = src.name
              AND tgt.deleted_at IS NULL
       );

    -- 3.2 COMPANY_* poblados desde la tabla companies del tenant destino.
    -- Estos identifican a la empresa (NIT, razon social, etc.) y no deben
    -- clonarse desde SIGCON DEMO.
    SELECT id, nit, dv, business_name, legal_representative, email, phone, address
      INTO v_company
      FROM companies WHERE id = p_company_id;

    IF v_company.id IS NOT NULL THEN
        PERFORM _upsert_parameter(p_company_id, 'COMPANY_NAME',                COALESCE(v_company.business_name, ''),        'COMPANY');
        PERFORM _upsert_parameter(p_company_id, 'COMPANY_NIT',                 COALESCE(v_company.nit, ''),                  'COMPANY');
        PERFORM _upsert_parameter(p_company_id, 'COMPANY_DV',                  COALESCE(v_company.dv, ''),                   'COMPANY');
        PERFORM _upsert_parameter(p_company_id, 'COMPANY_LEGAL_REPRESENTATIVE',COALESCE(v_company.legal_representative, ''), 'COMPANY');
        PERFORM _upsert_parameter(p_company_id, 'COMPANY_EMAIL',               COALESCE(v_company.email, ''),                'COMPANY');
        PERFORM _upsert_parameter(p_company_id, 'COMPANY_PHONE',               COALESCE(v_company.phone, ''),                'COMPANY');
        PERFORM _upsert_parameter(p_company_id, 'COMPANY_ADDRESS',             COALESCE(v_company.address, ''),              'COMPANY');
        PERFORM _upsert_parameter(p_company_id, 'COMPANY_SIZE',                'MEDIUM',                                     'COMPANY');
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION _upsert_parameter(
    p_company_id BIGINT, p_name TEXT, p_value TEXT, p_category TEXT
) RETURNS void AS $$
BEGIN
    INSERT INTO parameters (company_id, name, value, category, status, created_at, updated_at)
         SELECT p_company_id, p_name, p_value, p_category::varchar, 'ACTIVE', NOW(), NOW()
          WHERE NOT EXISTS (
              SELECT 1 FROM parameters
               WHERE company_id = p_company_id AND name = p_name AND deleted_at IS NULL
          );
END;
$$ LANGUAGE plpgsql;

-- Re-definir _tenant_auto_provision para incluir el seed de parameters.
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

    -- 4. 16 conceptos NOM legales
    PERFORM _seed_payroll_concepts_for_tenant(p_company_id);

    -- 5. 20 parameters (COMPANY_* + AGROFUSION_* + sigcon.nomina.*)
    PERFORM _seed_parameters_for_tenant(p_company_id);
END;
$$ LANGUAGE plpgsql;

-- -----------------------------------------------------------------------------
-- Parte 4: backfill de empresas existentes (#1)
-- ACME (id=2) y B SAS (id=3) no tienen periodos ni parameters.
-- Cualquier empresa en BD recibe ahora la provision completa (idempotente).
-- -----------------------------------------------------------------------------
DO $$
DECLARE rec RECORD; total INT;
BEGIN
    SELECT COUNT(*) INTO total FROM companies WHERE deleted_at IS NULL;
    RAISE NOTICE 'V10-H: backfilleando % empresa(s)', total;
    FOR rec IN SELECT id, business_name FROM companies WHERE deleted_at IS NULL ORDER BY id LOOP
        RAISE NOTICE 'V10-H: provision company_id=% (%)', rec.id, rec.business_name;
        PERFORM _tenant_auto_provision(rec.id, EXTRACT(YEAR FROM CURRENT_DATE)::INT);
    END LOOP;
END $$;
