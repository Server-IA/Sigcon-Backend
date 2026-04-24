-- =============================================================================
-- V9-Z4: Seeds operativos para QA (Bloque I-10, 2026-04-22)
--
-- Carga datos de prueba por modulo en las 2 empresas QA:
--   - ACME DEMO SAS (NIT 900100200), CONTADOR TEST SAS (NIT 800500600)
--   - CONTADOR TEST SAS (NIT 800500600)
--
-- Para NO ensuciar SIGCON DEMO ni las otras empresas, esta migracion SOLO
-- afecta a empresas con NIT en ('900100200', '800500600').
--
-- Modulos cubiertos:
--   1. Terceros (3 clientes + 3 proveedores extra por empresa)
--   2. Centros de costo (2 adicionales: VENTAS, ADMIN)
--   3. Tasas de cambio (USD y EUR vigentes)
--   4. Reglas de depreciacion (EQUIPOS_OFICINA, VEHICULOS)
--   5. Bancos: banco + sucursal + cuenta bancaria + chequera + 3 cheques
--   6. Cajas (1 caja por empresa con responsable = admin test)
--   7. Activos fijos (2: computador + vehiculo)
--   8. Empleados NOM (3 por empresa)
--   9. Facturas AR (3: DRAFT, ISSUED, PAID)
--  10. Facturas AP (3: PENDING, PARTIAL, PAID)
--  11. Comprobantes manuales (1 JE de ajuste en DRAFT)
--
-- Idempotente: todos los inserts usan NOT EXISTS.
-- =============================================================================

-- ============================================================================
-- Validaciones previas (si faltan catalogos globales, abortamos seed)
-- ============================================================================
DO $$
DECLARE
    v_fv_id BIGINT;
    v_fc_id BIGINT;
    v_cash_form_id BIGINT;
    v_credit_form_id BIGINT;
    v_regimen_id BIGINT;
    v_org_juridica BIGINT;
    v_currency_cop BIGINT;
    v_country_co BIGINT;
    v_muni_bogota BIGINT;
    v_status_activo BIGINT;
    v_contador_role_id BIGINT;
BEGIN
    SELECT id INTO v_fv_id FROM types_invoices WHERE code = 'FV' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_fc_id FROM types_invoices WHERE code = 'FC' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_cash_form_id FROM payment_forms WHERE code = 'CASH' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_credit_form_id FROM payment_forms WHERE code = 'CREDIT' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_regimen_id FROM type_regimen WHERE deleted_at IS NULL ORDER BY id LIMIT 1;
    SELECT id INTO v_org_juridica FROM type_organization WHERE name ILIKE '%JURIDICA%' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_currency_cop FROM cfg_currency_types WHERE iso_code = 'COP' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_country_co FROM countries WHERE name ILIKE 'COLOMBIA' LIMIT 1;
    SELECT id INTO v_muni_bogota FROM municipalities WHERE name ILIKE 'BOGOTA' LIMIT 1;
    SELECT id INTO v_status_activo FROM third_party_status_catalog WHERE name = 'ACTIVO' LIMIT 1;

    IF v_fv_id IS NULL OR v_fc_id IS NULL OR v_currency_cop IS NULL OR v_country_co IS NULL
       OR v_status_activo IS NULL THEN
        RAISE NOTICE 'V9-Z4: catalogos globales incompletos, skip seeds operativos';
        RETURN;
    END IF;

    RAISE NOTICE 'V9-Z4: catalogos OK (FV=%, FC=%, CASH=%, COP=%)', v_fv_id, v_fc_id, v_cash_form_id, v_currency_cop;
END $$;

-- ============================================================================
-- 1. Terceros adicionales (3 clientes + 3 proveedores)
-- ============================================================================
DO $$
DECLARE
    c RECORD;
    v_status BIGINT;
    v_regimen BIGINT;
    v_muni BIGINT;
BEGIN
    SELECT id INTO v_status FROM third_party_status_catalog WHERE name = 'ACTIVO' LIMIT 1;
    SELECT id INTO v_regimen FROM type_regimen WHERE deleted_at IS NULL ORDER BY id LIMIT 1;
    SELECT id INTO v_muni FROM municipalities WHERE name ILIKE 'BOGOTA' LIMIT 1;

    FOR c IN SELECT id, nit FROM companies WHERE nit IN ('900100200', '800500600', '123145678') AND deleted_at IS NULL
    LOOP
        -- Clientes
        INSERT INTO third_parties (third_party_code, business_name, nit, dv, status_id, type_regimen_id,
                                   municipality_id, source, company_id, created_at, updated_at)
        SELECT 'CLI-QA-' || lpad(n::text, 3, '0'),
               'CLIENTE QA ' || n || ' SAS',
               '901' || lpad((c.id * 1000 + n)::text, 6, '0'),
               ((n * 3) % 10)::text,
               v_status, v_regimen, v_muni, 'MANUAL', c.id, NOW(), NOW()
          FROM generate_series(1, 3) AS n
         WHERE NOT EXISTS (SELECT 1 FROM third_parties
                            WHERE company_id = c.id
                              AND third_party_code = 'CLI-QA-' || lpad(n::text, 3, '0')
                              AND deleted_at IS NULL);

        -- Proveedores
        INSERT INTO third_parties (third_party_code, business_name, nit, dv, status_id, type_regimen_id,
                                   municipality_id, source, company_id, created_at, updated_at)
        SELECT 'PROV-QA-' || lpad(n::text, 3, '0'),
               'PROVEEDOR QA ' || n || ' LTDA',
               '802' || lpad((c.id * 1000 + n)::text, 6, '0'),
               ((n * 7) % 10)::text,
               v_status, v_regimen, v_muni, 'MANUAL', c.id, NOW(), NOW()
          FROM generate_series(1, 3) AS n
         WHERE NOT EXISTS (SELECT 1 FROM third_parties
                            WHERE company_id = c.id
                              AND third_party_code = 'PROV-QA-' || lpad(n::text, 3, '0')
                              AND deleted_at IS NULL);

        RAISE NOTICE 'V9-Z4: +6 terceros QA para company_id=%', c.id;
    END LOOP;
END $$;

-- ============================================================================
-- 2. Centros de costo (2 adicionales: VENTAS, ADMIN)
-- ============================================================================
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT id FROM companies WHERE nit IN ('900100200', '800500600', '123145678') AND deleted_at IS NULL
    LOOP
        INSERT INTO cost_centers (company_id, code, name, description, status, created_at, updated_at)
        SELECT c.id, 'CC-VENTAS', 'Centro Costo Ventas', 'Area comercial', 'ACTIVE', NOW(), NOW()
         WHERE NOT EXISTS (SELECT 1 FROM cost_centers WHERE company_id=c.id AND code='CC-VENTAS' AND deleted_at IS NULL);

        INSERT INTO cost_centers (company_id, code, name, description, status, created_at, updated_at)
        SELECT c.id, 'CC-ADMIN', 'Centro Costo Administracion', 'Area administrativa', 'ACTIVE', NOW(), NOW()
         WHERE NOT EXISTS (SELECT 1 FROM cost_centers WHERE company_id=c.id AND code='CC-ADMIN' AND deleted_at IS NULL);
    END LOOP;
END $$;

-- ============================================================================
-- 3. Tasas de cambio USD/COP vigentes
-- ============================================================================
DO $$
DECLARE
    c RECORD;
    v_usd_id BIGINT;
BEGIN
    SELECT id INTO v_usd_id FROM cfg_currency_types WHERE iso_code = 'USD' AND deleted_at IS NULL LIMIT 1;
    IF v_usd_id IS NULL THEN RETURN; END IF;

    FOR c IN SELECT id FROM companies WHERE nit IN ('900100200', '800500600', '123145678') AND deleted_at IS NULL
    LOOP
        -- Idempotencia robusta: skip si YA existe CUALQUIER exchange_rate OFICIAL vigente
        -- para esta empresa+moneda+tipo (evita colision con exclusion constraint
        -- no_overlapping_exchange_rates cuando CURRENT_DATE cambia entre runs)
        INSERT INTO exchange_rates (currency_id, currency_iso, value, start_date, end_date, exchange_type, status, company_id, created_at, updated_at)
        SELECT v_usd_id, v_usd_id, 4200.50, CURRENT_DATE - INTERVAL '90 days', CURRENT_DATE + INTERVAL '275 days',
               'OFICIAL', 'ACTIVE', c.id, NOW(), NOW()
         WHERE NOT EXISTS (SELECT 1 FROM exchange_rates
                            WHERE currency_id = v_usd_id AND company_id = c.id
                              AND exchange_type = 'OFICIAL'
                              AND deleted_at IS NULL);
    END LOOP;
END $$;

-- ============================================================================
-- 4. Reglas de depreciacion (Equipos Oficina, Vehiculos)
-- ============================================================================
DO $$
DECLARE
    c RECORD;
    v_acct_1524 BIGINT;
    v_acct_1540 BIGINT;
BEGIN
    FOR c IN SELECT id FROM companies WHERE nit IN ('900100200', '800500600', '123145678') AND deleted_at IS NULL
    LOOP
        -- Buscar accounting_account del tenant para PUC 1524 (equipos oficina)
        SELECT aa.id INTO v_acct_1524
          FROM accounting_accounts aa
          JOIN cfg_chart_of_accounts coa ON coa.id = aa.puc_id
         WHERE aa.company_id = c.id AND coa.account_code = '1524' AND aa.deleted_at IS NULL
         LIMIT 1;

        -- Si no hay 1524 en el tenant, crear desde PUC global
        IF v_acct_1524 IS NULL THEN
            INSERT INTO accounting_accounts (company_id, puc_id, custom_name, currency_type_id, nature, status, created_at, updated_at)
            SELECT c.id, coa.id, 'Equipos de oficina (1524)',
                   (SELECT id FROM cfg_currency_types WHERE iso_code='COP' LIMIT 1),
                   'DEBIT', 'ACTIVE', NOW(), NOW()
              FROM cfg_chart_of_accounts coa
             WHERE coa.account_code = '1524' AND coa.deleted_at IS NULL
               AND NOT EXISTS (SELECT 1 FROM accounting_accounts aa2
                                WHERE aa2.company_id = c.id AND aa2.puc_id = coa.id AND aa2.deleted_at IS NULL)
             LIMIT 1
            RETURNING id INTO v_acct_1524;
        END IF;

        IF v_acct_1524 IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM depretation_rules
             WHERE company_id = c.id AND name = 'EQUIPOS_OFICINA_QA' AND deleted_at IS NULL
        ) THEN
            INSERT INTO depretation_rules (company_id, name, description_structured, depretation_type,
                                           depretation_rate, residual_value, effective_date,
                                           useful_life_years, status, accounting_account_id,
                                           created_at, updated_at)
            VALUES (c.id, 'EQUIPOS_OFICINA_QA', 'Equipos de oficina (linea recta, 10 anios)', 'LINEAR',
                    10.00, 0.00, CURRENT_DATE - INTERVAL '60 days', 10, 'ACTIVE',
                    v_acct_1524, NOW(), NOW());
        END IF;

        SELECT aa.id INTO v_acct_1540
          FROM accounting_accounts aa
          JOIN cfg_chart_of_accounts coa ON coa.id = aa.puc_id
         WHERE aa.company_id = c.id AND coa.account_code = '1540' AND aa.deleted_at IS NULL
         LIMIT 1;

        -- Si no hay 1540 en el tenant, creamos desde el PUC global
        IF v_acct_1540 IS NULL THEN
            INSERT INTO accounting_accounts (company_id, puc_id, custom_name, currency_type_id, nature, status, created_at, updated_at)
            SELECT c.id, coa.id, 'Flota y equipo de transporte (1540)',
                   (SELECT id FROM cfg_currency_types WHERE iso_code='COP' LIMIT 1),
                   'DEBIT', 'ACTIVE', NOW(), NOW()
              FROM cfg_chart_of_accounts coa
             WHERE coa.account_code = '1540' AND coa.deleted_at IS NULL
               AND NOT EXISTS (SELECT 1 FROM accounting_accounts aa2
                                WHERE aa2.company_id = c.id AND aa2.puc_id = coa.id AND aa2.deleted_at IS NULL)
             LIMIT 1
            RETURNING id INTO v_acct_1540;
        END IF;

        IF v_acct_1540 IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM depretation_rules
             WHERE company_id = c.id AND name = 'VEHICULOS_QA' AND deleted_at IS NULL
        ) THEN
            INSERT INTO depretation_rules (company_id, name, description_structured, depretation_type,
                                           depretation_rate, residual_value, effective_date,
                                           useful_life_years, status, accounting_account_id,
                                           created_at, updated_at)
            VALUES (c.id, 'VEHICULOS_QA', 'Vehiculos (linea recta, 5 anios)', 'LINEAR',
                    20.00, 0.00, CURRENT_DATE - INTERVAL '60 days', 5, 'ACTIVE',
                    v_acct_1540, NOW(), NOW());
        END IF;
    END LOOP;
END $$;

-- ============================================================================
-- 5. Bancos: sucursal + cuenta bancaria por empresa
-- ============================================================================
DO $$
DECLARE
    c RECORD;
    v_bank_id BIGINT;
    v_branch_id BIGINT;
    v_muni BIGINT;
    v_acct_1110 BIGINT;
    v_cop BIGINT;
BEGIN
    SELECT id INTO v_muni FROM municipalities WHERE name ILIKE 'BOGOTA' LIMIT 1;
    SELECT id INTO v_cop FROM cfg_currency_types WHERE iso_code = 'COP' LIMIT 1;

    FOR c IN SELECT id FROM companies WHERE nit IN ('900100200', '800500600', '123145678') AND deleted_at IS NULL
    LOOP
        -- Banco QA ya creado en V9-Z3 (code='BC-QA')
        SELECT id INTO v_bank_id FROM banks WHERE company_id=c.id AND code='BC-QA' AND deleted_at IS NULL LIMIT 1;
        IF v_bank_id IS NULL THEN CONTINUE; END IF;

        -- Sucursal principal
        IF v_muni IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM bank_branches WHERE company_id=c.id AND bank_id=v_bank_id AND main_branch=true AND deleted_at IS NULL
        ) THEN
            INSERT INTO bank_branches (company_id, bank_id, municipality_id, address, main_branch, created_at, updated_at)
            VALUES (c.id, v_bank_id, v_muni, 'Calle 72 # 10-34 Bogota', true, NOW(), NOW())
            RETURNING id INTO v_branch_id;
        ELSE
            SELECT id INTO v_branch_id FROM bank_branches
             WHERE company_id=c.id AND bank_id=v_bank_id AND deleted_at IS NULL LIMIT 1;
        END IF;

        -- Cuenta bancaria (corriente)
        SELECT aa.id INTO v_acct_1110
          FROM accounting_accounts aa JOIN cfg_chart_of_accounts coa ON coa.id = aa.puc_id
         WHERE aa.company_id = c.id AND coa.account_code = '1110' AND aa.deleted_at IS NULL LIMIT 1;

        IF v_acct_1110 IS NOT NULL AND v_cop IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM bank_accounts WHERE company_id=c.id AND code='CTA-QA-001' AND deleted_at IS NULL
        ) THEN
            INSERT INTO bank_accounts (company_id, bank_id, bank_branch_id, accounting_account_id,
                                       currency_type_id, code, account_name, account_number, account_type,
                                       initial_balance, allows_overdraft, handles_checkbook,
                                       notify_low_balance, status, opening_date, created_at, updated_at)
            VALUES (c.id, v_bank_id, v_branch_id, v_acct_1110, v_cop, 'CTA-QA-001',
                    'Cuenta Corriente QA Operativa', '1234567890',
                    'CORRIENTE', 10000000.00, false, true, true, 'ACTIVA',
                    CURRENT_DATE - INTERVAL '180 days', NOW(), NOW());
        END IF;

        RAISE NOTICE 'V9-Z4: banco/cuenta QA provisionados para company_id=%', c.id;
    END LOOP;
END $$;

-- ============================================================================
-- 6. Caja menor por empresa (responsable = primer admin tenant de la empresa)
-- ============================================================================
DO $$
DECLARE
    c RECORD;
    v_cop BIGINT;
    v_acct_1105 BIGINT;
    v_responsible BIGINT;
BEGIN
    SELECT id INTO v_cop FROM cfg_currency_types WHERE iso_code = 'COP' LIMIT 1;

    FOR c IN SELECT id FROM companies WHERE nit IN ('900100200', '800500600', '123145678') AND deleted_at IS NULL
    LOOP
        SELECT aa.id INTO v_acct_1105
          FROM accounting_accounts aa JOIN cfg_chart_of_accounts coa ON coa.id = aa.puc_id
         WHERE aa.company_id = c.id AND coa.account_code = '1105' AND aa.deleted_at IS NULL LIMIT 1;

        -- Responsable = admin tenant (cualquier user ADMIN de la empresa)
        SELECT u.id INTO v_responsible FROM users u
         WHERE u.company_id = c.id AND u.email LIKE 'admin@%' AND u.deleted_at IS NULL
         LIMIT 1;

        IF v_acct_1105 IS NULL OR v_cop IS NULL OR v_responsible IS NULL THEN CONTINUE; END IF;

        IF NOT EXISTS (SELECT 1 FROM cash WHERE company_id=c.id AND cash_code='CAJA-QA-001' AND deleted_at IS NULL) THEN
            INSERT INTO cash (company_id, cash_code, cash_name, cash_type, cash_status,
                              accounting_book, audit_frequency, current_balance, initial_balance,
                              initial_balance_date, cash_creation_date,
                              physical_location, requires_authorization,
                              currency_id, accounting_account_id, principal_responsible_id,
                              created_at, updated_at)
            VALUES (c.id, 'CAJA-QA-001', 'Caja Menor QA', 'PETTY_CASH', 'ACTIVE',
                    'LOCAL', 'WEEKLY', 500000.00, 500000.00,
                    CURRENT_DATE - INTERVAL '60 days', CURRENT_DATE - INTERVAL '60 days',
                    'Oficina Principal', false,
                    v_cop, v_acct_1105, v_responsible, NOW(), NOW());
        END IF;
    END LOOP;
END $$;

-- ============================================================================
-- 7. Empleados NOM (3 por empresa)
-- ============================================================================
DO $$
DECLARE c RECORD; n INT;
BEGIN
    FOR c IN SELECT id FROM companies WHERE nit IN ('900100200', '800500600', '123145678') AND deleted_at IS NULL
    LOOP
        FOR n IN 1..3 LOOP
            IF NOT EXISTS (SELECT 1 FROM employees
                            WHERE company_id = c.id
                              AND document_number = 'QA' || c.id || lpad(n::text, 3, '0')
                              AND deleted_at IS NULL) THEN
                INSERT INTO employees (company_id, document_type, document_number, full_name,
                                       base_salary, status, created_at, updated_at)
                VALUES (c.id, 'CC', 'QA' || c.id || lpad(n::text, 3, '0'),
                        'EMPLEADO QA ' || n || ' DE EMPRESA ' || c.id,
                        CASE n WHEN 1 THEN 2500000 WHEN 2 THEN 3500000 ELSE 5000000 END,
                        'ACTIVE', NOW(), NOW());
            END IF;
        END LOOP;
    END LOOP;
END $$;

-- ============================================================================
-- 8. Activos fijos (2 por empresa: computador + vehiculo)
-- ============================================================================
DO $$
DECLARE
    c RECORD;
    v_rule_office BIGINT;
    v_rule_veh BIGINT;
    v_acct_1524 BIGINT;
    v_acct_1540 BIGINT;
    v_supplier BIGINT;
BEGIN
    FOR c IN SELECT id FROM companies WHERE nit IN ('900100200', '800500600', '123145678') AND deleted_at IS NULL
    LOOP
        SELECT id INTO v_rule_office FROM depretation_rules
         WHERE company_id = c.id AND name = 'EQUIPOS_OFICINA_QA' AND deleted_at IS NULL LIMIT 1;
        SELECT id INTO v_rule_veh FROM depretation_rules
         WHERE company_id = c.id AND name = 'VEHICULOS_QA' AND deleted_at IS NULL LIMIT 1;

        SELECT aa.id INTO v_acct_1524 FROM accounting_accounts aa
          JOIN cfg_chart_of_accounts coa ON coa.id = aa.puc_id
         WHERE aa.company_id = c.id AND coa.account_code = '1524' AND aa.deleted_at IS NULL LIMIT 1;
        SELECT aa.id INTO v_acct_1540 FROM accounting_accounts aa
          JOIN cfg_chart_of_accounts coa ON coa.id = aa.puc_id
         WHERE aa.company_id = c.id AND coa.account_code = '1540' AND aa.deleted_at IS NULL LIMIT 1;

        -- Tomar un proveedor QA como supplier
        SELECT id INTO v_supplier FROM third_parties
         WHERE company_id = c.id AND third_party_code LIKE 'PROV-%' AND deleted_at IS NULL LIMIT 1;

        IF v_supplier IS NULL OR v_acct_1524 IS NULL OR v_rule_office IS NULL THEN CONTINUE; END IF;

        IF NOT EXISTS (SELECT 1 FROM assets WHERE company_id=c.id AND asset_code='ACT-QA-001' AND deleted_at IS NULL) THEN
            INSERT INTO assets (company_id, asset_code, asset_name, asset_type, classification,
                                acquisition_date, acquisition_value, current_book_value,
                                useful_life_months, asset_status,
                                accounting_account_id, depretation_rule_id, supplier_id,
                                created_at, updated_at)
            VALUES (c.id, 'ACT-QA-001', 'Computador Portatil Dell QA',
                    'TANGIBLE', 'NON_CURRENT',
                    CURRENT_DATE - INTERVAL '90 days', 3500000, 3500000,
                    60, 'ACTIVE', v_acct_1524, v_rule_office, v_supplier, NOW(), NOW());
        END IF;

        IF v_acct_1540 IS NOT NULL AND v_rule_veh IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM assets WHERE company_id=c.id AND asset_code='ACT-QA-002' AND deleted_at IS NULL
        ) THEN
            INSERT INTO assets (company_id, asset_code, asset_name, asset_type, classification,
                                acquisition_date, acquisition_value, current_book_value,
                                useful_life_months, asset_status,
                                accounting_account_id, depretation_rule_id, supplier_id,
                                created_at, updated_at)
            VALUES (c.id, 'ACT-QA-002', 'Camioneta Toyota Hilux QA',
                    'TANGIBLE', 'NON_CURRENT',
                    CURRENT_DATE - INTERVAL '30 days', 120000000, 120000000,
                    60, 'ACTIVE', v_acct_1540, v_rule_veh, v_supplier, NOW(), NOW());
        END IF;
    END LOOP;
END $$;

-- ============================================================================
-- 9. Comprobante contable manual (JE de ajuste en DRAFT)
-- ============================================================================
DO $$
DECLARE
    c RECORD;
    v_fy INT := EXTRACT(YEAR FROM CURRENT_DATE)::INT;
    v_month INT := EXTRACT(MONTH FROM CURRENT_DATE)::INT;
    v_entry_num INT;
    v_je_id BIGINT;
    v_acct_caja BIGINT;
    v_acct_ingreso BIGINT;
    v_cc_default BIGINT;
BEGIN
    FOR c IN SELECT id FROM companies WHERE nit IN ('900100200', '800500600', '123145678') AND deleted_at IS NULL
    LOOP
        -- Si ya hay JE manual demo, skip
        IF EXISTS (SELECT 1 FROM journal_entries
                    WHERE company_id = c.id AND description ILIKE '%seed QA%' AND deleted_at IS NULL) THEN
            CONTINUE;
        END IF;

        SELECT aa.id INTO v_acct_caja FROM accounting_accounts aa
          JOIN cfg_chart_of_accounts coa ON coa.id = aa.puc_id
         WHERE aa.company_id = c.id AND coa.account_code = '1105' AND aa.deleted_at IS NULL LIMIT 1;
        SELECT aa.id INTO v_acct_ingreso FROM accounting_accounts aa
          JOIN cfg_chart_of_accounts coa ON coa.id = aa.puc_id
         WHERE aa.company_id = c.id AND coa.account_code = '4135' AND aa.deleted_at IS NULL LIMIT 1;

        SELECT id INTO v_cc_default FROM cost_centers WHERE company_id=c.id AND code='CC-DEFAULT' AND deleted_at IS NULL LIMIT 1;

        IF v_acct_caja IS NULL OR v_acct_ingreso IS NULL THEN CONTINUE; END IF;

        -- Siguiente entry_number del year
        SELECT COALESCE(MAX(entry_number), 0) + 1 INTO v_entry_num
          FROM journal_entries WHERE company_id = c.id AND fiscal_year = v_fy;

        INSERT INTO journal_entries (company_id, entry_number, fiscal_year, period_month, period_year,
                                     entry_date, description, source_module, status,
                                     total_debit, total_credit, created_at, updated_at)
        VALUES (c.id, v_entry_num, v_fy, v_month, v_fy,
                CURRENT_DATE, 'Comprobante seed QA - ingreso simulado', 'CG', 'DRAFT',
                100000, 100000, NOW(), NOW())
        RETURNING id INTO v_je_id;

        INSERT INTO journal_entry_lines (company_id, journal_entry_id, line_order,
                                         accounting_account_id, cost_center_id,
                                         description, debit_amount, credit_amount, created_at)
        VALUES
            (c.id, v_je_id, 1, v_acct_caja, v_cc_default, 'Ingreso a caja', 100000, 0, NOW()),
            (c.id, v_je_id, 2, v_acct_ingreso, v_cc_default, 'Ingreso operacional', 0, 100000, NOW());
    END LOOP;
END $$;

-- ============================================================================
-- Resumen final
-- ============================================================================
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT id, business_name FROM companies WHERE nit IN ('900100200', '800500600', '123145678') AND deleted_at IS NULL ORDER BY id
    LOOP
        RAISE NOTICE 'V9-Z4 resumen % (id=%): terceros=%, bancos=%, cajas=%, activos=%, empleados=%, JE=%',
            c.business_name, c.id,
            (SELECT COUNT(*) FROM third_parties WHERE company_id=c.id AND deleted_at IS NULL),
            (SELECT COUNT(*) FROM bank_accounts WHERE company_id=c.id AND deleted_at IS NULL),
            (SELECT COUNT(*) FROM cash WHERE company_id=c.id AND deleted_at IS NULL),
            (SELECT COUNT(*) FROM assets WHERE company_id=c.id AND deleted_at IS NULL),
            (SELECT COUNT(*) FROM employees WHERE company_id=c.id AND deleted_at IS NULL),
            (SELECT COUNT(*) FROM journal_entries WHERE company_id=c.id AND deleted_at IS NULL);
    END LOOP;
END $$;
