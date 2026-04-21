-- =============================================================================
-- V10-D: UNIQUE compuestos con company_id + tabla de configuracion tenant-scoped
-- Fecha: 2026-04-19
-- Bloque D - Etapa 6: consecutivos/numeros independientes por empresa.
--
-- Antes: varios UNIQUE globales asumian un solo tenant (p.ej. invoice_number UNIQUE
-- impediria que empresa A y B ambas tengan "FV-2026000001").
-- Despues: cada empresa genera su propio consecutivo; UNIQUE(company_id, <numero>).
-- =============================================================================

-- Helper: drop UNIQUE por nombre dinamico
CREATE OR REPLACE FUNCTION _drop_unique(tbl text, colname text) RETURNS void AS $$
DECLARE c_name text;
BEGIN
    -- Encuentra constraint UNIQUE simple sobre la columna (ignorando compuestos)
    SELECT conname INTO c_name
      FROM pg_constraint c
      JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
     WHERE c.conrelid = tbl::regclass
       AND c.contype = 'u'
       AND a.attname = colname
       AND array_length(c.conkey, 1) = 1
     LIMIT 1;
    IF c_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', tbl, c_name);
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION _ensure_unique_composite(tbl text, uk_name text, cols text) RETURNS void AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = uk_name AND conrelid = tbl::regclass
    ) THEN
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I UNIQUE (%s)', tbl, uk_name, cols);
    END IF;
END;
$$ LANGUAGE plpgsql;

-- 1) sales_invoices.invoice_number -> (company_id, invoice_number)
SELECT _drop_unique('sales_invoices', 'invoice_number');
SELECT _ensure_unique_composite('sales_invoices', 'uk_sales_invoices_company_number', 'company_id, invoice_number');

-- 2) ar_credit_debit_notes.note_number -> (company_id, note_number)
SELECT _drop_unique('ar_credit_debit_notes', 'note_number');
SELECT _ensure_unique_composite('ar_credit_debit_notes', 'uk_ar_notes_company_number', 'company_id, note_number');

-- 3) dian_resolutions.resolution_number -> (company_id, resolution_number)
SELECT _drop_unique('dian_resolutions', 'resolution_number');
SELECT _ensure_unique_composite('dian_resolutions', 'uk_dian_res_company_number', 'company_id, resolution_number');

-- 4) account_mappings.concept_code -> (company_id, concept_code)
SELECT _drop_unique('account_mappings', 'concept_code');
DROP INDEX IF EXISTS ux_account_mappings_concept;  -- UNIQUE parcial legacy pre-V10-A
SELECT _ensure_unique_composite('account_mappings', 'uk_account_mappings_company_concept', 'company_id, concept_code');
CREATE UNIQUE INDEX IF NOT EXISTS ux_account_mappings_company_concept_active
    ON account_mappings (company_id, concept_code) WHERE deleted_at IS NULL;

-- 5) bnk_cash_flow_projections.name -> (company_id, name)
SELECT _drop_unique('bnk_cash_flow_projections', 'name');
SELECT _ensure_unique_composite('bnk_cash_flow_projections', 'uk_cashflow_company_name', 'company_id, name');

-- 6) risk_segmentation.client_id -> (company_id, client_id) (un cliente 1x por empresa)
SELECT _drop_unique('risk_segmentation', 'client_id');
SELECT _ensure_unique_composite('risk_segmentation', 'uk_risksegm_company_client', 'company_id, client_id');

-- 7) journal_entries consecutivos: (company_id, fiscal_year, entry_number)
SELECT _ensure_unique_composite('journal_entries', 'uk_journal_entries_company_fy_num',
                                 'company_id, fiscal_year, entry_number');

-- 8) vouchers: (company_id, voucher_type_id, number) — legacy pero tenant-scoped
SELECT _ensure_unique_composite('vouchers', 'uk_vouchers_company_type_num',
                                 'company_id, voucher_type_id, number');

-- 9) accounting_accounts.custom_name UNIQUE(custom_name) parcial -> (company_id, custom_name)
DROP INDEX IF EXISTS uk_accounting_account_custom_name_active;
CREATE UNIQUE INDEX IF NOT EXISTS uk_accounting_account_company_name_active
    ON accounting_accounts (company_id, custom_name) WHERE deleted_at IS NULL;

-- 9.1) drop legacy single-tenant unique indexes que aun puedan persistir
DROP INDEX IF EXISTS uk_accounting_periods_year_month;
DROP INDEX IF EXISTS uk_sales_invoice_number;
DROP INDEX IF EXISTS uk_ar_note_number;
DROP INDEX IF EXISTS uk_dian_resolution_number;
DROP INDEX IF EXISTS uk_ap_payment_ref;
DROP INDEX IF EXISTS uk_ap_note_number;
DROP INDEX IF EXISTS uk_po_number;
DROP INDEX IF EXISTS uk_gr_number;
DROP INDEX IF EXISTS uk_ar_payment_reference;

-- 9.2) Compuestos parciales para AP + AR (consecutivos por empresa)
CREATE UNIQUE INDEX IF NOT EXISTS uk_ap_payment_ref_company
    ON ap_payments (company_id, payment_reference)
    WHERE deleted_at IS NULL AND payment_reference IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ap_note_number_company
    ON ap_credit_debit_notes (company_id, note_number) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_po_number_company
    ON purchase_orders (company_id, order_number) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_gr_number_company
    ON goods_receipts (company_id, receipt_number) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ar_payment_ref_company
    ON ar_payments (company_id, payment_reference)
    WHERE deleted_at IS NULL AND payment_reference IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_invoice_number_company
    ON sales_invoices (company_id, invoice_number) WHERE deleted_at IS NULL;

-- 9.3) third_parties: UNIQUE(third_party_code) global -> (company_id, third_party_code)
-- + UNIQUE(nit, dv) global -> (company_id, nit, dv). Esto permite que cada empresa
-- tenga su propio TER-YYYY#### sin colision cross-tenant.
DROP INDEX IF EXISTS uk_third_parties_code_active;
DROP INDEX IF EXISTS uk_third_parties_nit_dv_active;
CREATE UNIQUE INDEX IF NOT EXISTS uk_third_parties_company_code_active
    ON third_parties (company_id, third_party_code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_third_parties_company_nit_dv_active
    ON third_parties (company_id, nit, dv) WHERE deleted_at IS NULL;

-- 10) cost_centers.code/name UNIQUE parcial -> (company_id, code) y (company_id, name)
DROP INDEX IF EXISTS uk_cost_center_code_active;
DROP INDEX IF EXISTS uk_cost_center_name_active;
CREATE UNIQUE INDEX IF NOT EXISTS uk_cost_center_company_code_active
    ON cost_centers (company_id, code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_cost_center_company_name_active
    ON cost_centers (company_id, name) WHERE deleted_at IS NULL;

-- Cleanup
DROP FUNCTION _drop_unique(text, text);
DROP FUNCTION _ensure_unique_composite(text, text, text);

-- =============================================================================
-- Seed reusable: funcion que auto-provisiona una empresa nueva con:
--   - 12 periodos del ano actual (estado OPEN)
--   - 18 account_mappings default (si el PUC base ya existe en cfg_chart_of_accounts)
--   - 1 centro de costo default "CC-DEFAULT"
--
-- Se invoca desde CompanyService.create via Spring Events o directamente.
-- Tambien se usa en esta migracion para asegurar que SIGCON DEMO (id=1) tenga
-- todos los periodos actuales y AGROINSUMOS (id=2) arranque con el setup completo.
-- =============================================================================

CREATE OR REPLACE FUNCTION _tenant_auto_provision(p_company_id BIGINT, p_year INT) RETURNS void AS $$
DECLARE
    v_month INT;
    v_acct_id BIGINT;
BEGIN
    -- 1. Periodos del ano (idempotente via UNIQUE(company_id, year, month))
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

    -- 3. 18 mapeos contables default (PUC Colombia Decreto 2650/1993)
    -- Los mapeos referencian accounting_accounts, que son tenant-scoped. Si la empresa
    -- no tiene todavia una cuenta con ese PUC code, la creamos automaticamente.
    -- Nature: DEBIT (activos, gastos, costos) o CREDIT (pasivos, patrimonio, ingresos)
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
END;
$$ LANGUAGE plpgsql;

-- Helper: resuelve/crea la accounting_account con el PUC code dado para la empresa,
-- y upsertea el mapping (concept_code, company_id) -> accounting_account_id.
CREATE OR REPLACE FUNCTION _ensure_tenant_account_mapping(
    p_company_id BIGINT, p_concept_code TEXT, p_puc_code TEXT,
    p_custom_name TEXT, p_nature TEXT
) RETURNS void AS $$
DECLARE
    v_puc_id BIGINT;
    v_acct_id BIGINT;
    v_currency_id BIGINT;
BEGIN
    -- 1. Buscar PUC en cfg_chart_of_accounts (tabla global)
    SELECT id INTO v_puc_id FROM cfg_chart_of_accounts
     WHERE account_code = p_puc_code AND deleted_at IS NULL LIMIT 1;
    IF v_puc_id IS NULL THEN
        RAISE NOTICE 'PUC % no existe en cfg_chart_of_accounts, skip mapping %', p_puc_code, p_concept_code;
        RETURN;
    END IF;

    -- 2. Moneda default (COP = id 1 usualmente)
    SELECT id INTO v_currency_id FROM cfg_currency_types WHERE iso_code = 'COP' AND deleted_at IS NULL LIMIT 1;
    IF v_currency_id IS NULL THEN
        SELECT id INTO v_currency_id FROM cfg_currency_types WHERE deleted_at IS NULL LIMIT 1;
    END IF;

    -- 3. accounting_account del tenant para ese PUC (buscar o crear)
    SELECT id INTO v_acct_id FROM accounting_accounts
     WHERE company_id = p_company_id AND puc_id = v_puc_id AND deleted_at IS NULL
     LIMIT 1;
    IF v_acct_id IS NULL THEN
        INSERT INTO accounting_accounts (
            company_id, puc_id, custom_name, currency_type_id, nature, status,
            created_at, updated_at
        )
        VALUES (
            p_company_id, v_puc_id,
            -- Evita colision del UNIQUE parcial (company_id, custom_name) si varios conceptos
            -- apuntan al mismo PUC (p.ej. AP_IVA_DESCONTABLE y AR_IVA_GENERADO -> 2408).
            p_custom_name || ' (' || p_puc_code || ')',
            v_currency_id, p_nature, 'ACTIVE',
            NOW(), NOW()
        )
        RETURNING id INTO v_acct_id;
    END IF;

    -- 4. Upsert mapping (UNIQUE(company_id, concept_code))
    INSERT INTO account_mappings (
        company_id, concept_code, accounting_account_id, concept_description, puc_code,
        created_at, updated_at
    ) VALUES (
        p_company_id, p_concept_code, v_acct_id,
        'Auto-provisioned al crear empresa (V10-D)', p_puc_code,
        NOW(), NOW()
    )
    ON CONFLICT ON CONSTRAINT uk_account_mappings_company_concept DO NOTHING;
END;
$$ LANGUAGE plpgsql;

-- Default de company_id=1 en TODAS las tablas tenant-scoped. Permite que scripts
-- seed legacy (V3, V9-*, V32, etc.) que insertan sin company_id no rompan el arranque.
-- El @PrePersist de la entidad sobreescribe con el tenant actual en runtime JPA.
DO $$
DECLARE
    t text;
BEGIN
    FOR t IN
        SELECT DISTINCT table_name
          FROM information_schema.columns
         WHERE column_name = 'company_id'
           AND table_schema = 'public'
           AND is_nullable = 'NO'
           -- users tiene ck_users_tenant_or_platform: PLATFORM_ADMIN debe ser NULL.
           AND table_name <> 'users'
    LOOP
        EXECUTE format('ALTER TABLE %I ALTER COLUMN company_id SET DEFAULT 1', t);
    END LOOP;
END $$;

-- Provisionar SIGCON DEMO (empresa 1) con el setup del ano actual.
-- Empresas adicionales se auto-provisionan via CompanyService.create (Bloque D).
SELECT _tenant_auto_provision(1, EXTRACT(YEAR FROM CURRENT_DATE)::INT);
-- Si la empresa 2 (AGROINSUMOS) ya existe (BD con datos previos), provisionarla tambien.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM companies WHERE id = 2 AND deleted_at IS NULL) THEN
        PERFORM _tenant_auto_provision(2, EXTRACT(YEAR FROM CURRENT_DATE)::INT);
    END IF;
END $$;
