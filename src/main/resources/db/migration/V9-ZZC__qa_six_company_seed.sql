-- =============================================================================
-- V9-ZZC — Seed de 6 empresas QA con datos operativos completos
-- =============================================================================
--
-- Reemplaza todas las empresas QA legadas por un set fresco y consistente
-- de 6 empresas idénticas, cada una con:
--
--   - 3 usuarios (admin / contador / auditor) con password 'Passw0rd!'
--   - 8 terceros (4 clientes + 4 proveedores) con NITs colombianos
--   - Cuentas contables auto-provisionadas + 4 cuentas extra (1305 cliente,
--     2205 proveedor, 1110 banco, 1105 caja) operativas
--   - 1 banco + 1 sucursal + 1 cuenta corriente + 1 caja menor
--   - 1 chequera con 5 cheques (3 emitidos, 2 disponibles)
--   - 5 movimientos financieros (depositos + retiros + transferencias)
--   - 1 resolución DIAN activa
--   - 3 facturas venta (1 PAID, 1 PARTIALLY_PAID, 1 ISSUED) + 2 cobros
--   - 3 facturas compra + 2 pagos (con sus JE asociados)
--   - 3 activos (computador, vehículo, mueble) + datos para depreciación
--   - 4 empleados completos para liquidación NOM
--   - 5 comprobantes contables manuales (DRAFT y POSTED)
--
-- Cada grupo QA escoge una empresa y la "rompe" sin afectar a las otras.
-- La migración es IDEMPOTENTE: re-ejecutar no duplica nada (usa marcadores
-- únicos en NIT y emails).
--
-- IMPORTANTE: SIGCON DEMO (id=1) NO se toca — es la baseline del sistema.
-- =============================================================================

-- =============================================================================
-- PASO 0 — Soft-delete empresas QA legadas (id 2-6) y sus usuarios.
-- Mantenemos id=1 SIGCON DEMO. Datos viejos quedan preservados (deleted_at)
-- pero nadie podrá loguearse en ellos (AuthService bloquea empresa INACTIVE).
-- =============================================================================
UPDATE companies SET deleted_at = NOW(), status = 'INACTIVE'
WHERE id BETWEEN 2 AND 6 AND deleted_at IS NULL;

UPDATE users SET deleted_at = NOW(), status = 'INACTIVE'
WHERE company_id BETWEEN 2 AND 6 AND deleted_at IS NULL;

-- =============================================================================
-- PASO 1 — Función de seed reutilizable.
-- Recibe: company_id (debe existir y estar auto-provisionado), suffix (1..6)
-- Pobla todos los módulos con datos operativos realistas.
-- =============================================================================
CREATE OR REPLACE FUNCTION _qa_seed_company(p_company_id BIGINT, p_suffix INT)
RETURNS VOID AS $$
DECLARE
    v_pwd_hash TEXT := '$2a$10$BCLWV2zEWlOnAhn/td3jguIuwAlPjYSQlzBI95qVpieF0VpBxN1Mq'; -- 'Passw0rd!' (mismo que seeds V9-Z3)
    v_admin_role_id BIGINT;
    v_contador_role_id BIGINT;
    v_auditor_role_id BIGINT;
    v_admin_id BIGINT;
    v_contador_id BIGINT;
    v_auditor_id BIGINT;
    v_status_active_id BIGINT;
    v_typeorg_juridica_id BIGINT;
    v_typereg_responsable_id BIGINT;
    v_municipality_bog_id BIGINT;
    v_currency_cop_id BIGINT;
    v_acct_clientes_id BIGINT;     -- PUC 1305
    v_acct_proveedores_id BIGINT;  -- PUC 2205
    v_acct_bancos_id BIGINT;       -- PUC 1110
    v_acct_caja_id BIGINT;         -- PUC 1105
    v_acct_ingresos_id BIGINT;     -- PUC 4135
    v_acct_gastos_id BIGINT;       -- PUC 5135
    v_acct_iva_id BIGINT;          -- PUC 2408
    v_acct_equipo_oficina_id BIGINT; -- PUC 1524
    v_acct_equipo_compute_id BIGINT; -- PUC 1528
    v_acct_flota_id BIGINT;          -- PUC 1540
    v_cliente1_id BIGINT;
    v_cliente2_id BIGINT;
    v_cliente3_id BIGINT;
    v_cliente4_id BIGINT;
    v_proveedor1_id BIGINT;
    v_proveedor2_id BIGINT;
    v_proveedor3_id BIGINT;
    v_proveedor4_id BIGINT;
    v_bank_id BIGINT;
    v_branch_id BIGINT;
    v_bank_acct_id BIGINT;
    v_cash_id BIGINT;
    v_checkbook_id BIGINT;
    v_depr_oficina_id BIGINT;
    v_depr_compute_id BIGINT;
    v_depr_flota_id BIGINT;
    v_je_id BIGINT;
    v_fv_id BIGINT;
    v_fc_id BIGINT;
    v_payment_form_credito_id BIGINT;
    v_typeinv_fc_id BIGINT;
    v_typeinv_fv_id BIGINT;
    v_invstate_pending_id BIGINT;
    v_year INT := EXTRACT(YEAR FROM CURRENT_DATE)::INT;
    v_today DATE := CURRENT_DATE;
    v_period_id BIGINT;
BEGIN
    RAISE NOTICE '_qa_seed_company: poblando empresa id=% (suffix=%)', p_company_id, p_suffix;

    -- --------------------------------------------------------------------- --
    -- Catalogos globales (no varían por empresa)
    -- --------------------------------------------------------------------- --
    -- Crear rol ADMIN si no existe (V9-J solo crea CONTADOR/AUXILIAR/AUDITOR;
    -- ADMIN normalmente lo crea V14 pero corre despues de V9-ZZC alfabeticamente)
    INSERT INTO roles (name, status, created_at, updated_at)
    SELECT 'ADMIN', 'ACTIVE', NOW(), NOW()
     WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name='ADMIN' AND deleted_at IS NULL);

    SELECT id INTO v_admin_role_id    FROM roles WHERE name='ADMIN'    AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_contador_role_id FROM roles WHERE name='CONTADOR' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_auditor_role_id  FROM roles WHERE name='AUDITOR'  AND deleted_at IS NULL LIMIT 1;
    -- Fallback defensivo: si por algun motivo no existen los roles funcionales,
    -- usar ADMIN para los tres (todos los usuarios podran hacer todo en QA).
    IF v_contador_role_id IS NULL THEN v_contador_role_id := v_admin_role_id; END IF;
    IF v_auditor_role_id IS NULL THEN v_auditor_role_id := v_admin_role_id; END IF;
    SELECT id INTO v_status_active_id FROM third_party_status_catalog WHERE name='ACTIVO' LIMIT 1;
    SELECT id INTO v_typeorg_juridica_id FROM type_organization WHERE name='PERSONA JURIDICA' LIMIT 1;
    SELECT id INTO v_typereg_responsable_id FROM type_regimen WHERE name LIKE '%RESPONSABLE%' AND name NOT LIKE '%NO%' LIMIT 1;
    SELECT id INTO v_municipality_bog_id FROM municipalities WHERE name='BOGOTA' LIMIT 1;
    SELECT id INTO v_currency_cop_id FROM cfg_currency_types WHERE iso_code='COP' LIMIT 1;
    SELECT id INTO v_payment_form_credito_id FROM payment_forms WHERE name='Credito' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_typeinv_fc_id FROM types_invoices WHERE code='FC' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_typeinv_fv_id FROM types_invoices WHERE code='FV' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_invstate_pending_id FROM invoice_states WHERE deleted_at IS NULL ORDER BY id LIMIT 1;

    -- --------------------------------------------------------------------- --
    -- Cuentas contables operativas — leemos las que el auto-provision creó
    -- via account_mappings (AR_CLIENTES, AP_PROVEEDORES, BANCOS_DEFAULT, etc.)
    -- --------------------------------------------------------------------- --
    SELECT accounting_account_id INTO v_acct_clientes_id     FROM account_mappings WHERE company_id=p_company_id AND concept_code='AR_CLIENTES' LIMIT 1;
    SELECT accounting_account_id INTO v_acct_proveedores_id  FROM account_mappings WHERE company_id=p_company_id AND concept_code='AP_PROVEEDORES' LIMIT 1;
    SELECT accounting_account_id INTO v_acct_bancos_id       FROM account_mappings WHERE company_id=p_company_id AND concept_code='BANCOS_DEFAULT' LIMIT 1;
    SELECT accounting_account_id INTO v_acct_caja_id         FROM account_mappings WHERE company_id=p_company_id AND concept_code='CAJA_DEFAULT' LIMIT 1;
    SELECT accounting_account_id INTO v_acct_ingresos_id     FROM account_mappings WHERE company_id=p_company_id AND concept_code='AR_INGRESOS' LIMIT 1;
    SELECT accounting_account_id INTO v_acct_gastos_id       FROM account_mappings WHERE company_id=p_company_id AND concept_code='AP_COMPRAS_DEFAULT' LIMIT 1;
    SELECT accounting_account_id INTO v_acct_iva_id          FROM account_mappings WHERE company_id=p_company_id AND concept_code='AP_IVA_DESCONTABLE' LIMIT 1;

    IF v_acct_clientes_id IS NULL OR v_acct_proveedores_id IS NULL OR v_acct_bancos_id IS NULL THEN
        RAISE EXCEPTION 'Faltan account_mappings críticos para company_id=% — ejecuta _tenant_auto_provision primero', p_company_id;
    END IF;

    -- Cuentas PPE (Equipo oficina, computación, flota) — auto-creadas por V9-Z7
    SELECT aa.id INTO v_acct_equipo_oficina_id
      FROM accounting_accounts aa
      JOIN cfg_chart_of_accounts puc ON puc.id = aa.puc_id
     WHERE aa.company_id=p_company_id AND puc.account_code='1524' AND aa.deleted_at IS NULL
     ORDER BY aa.id LIMIT 1;
    SELECT aa.id INTO v_acct_equipo_compute_id
      FROM accounting_accounts aa
      JOIN cfg_chart_of_accounts puc ON puc.id = aa.puc_id
     WHERE aa.company_id=p_company_id AND puc.account_code='1528' AND aa.deleted_at IS NULL
     ORDER BY aa.id LIMIT 1;
    SELECT aa.id INTO v_acct_flota_id
      FROM accounting_accounts aa
      JOIN cfg_chart_of_accounts puc ON puc.id = aa.puc_id
     WHERE aa.company_id=p_company_id AND puc.account_code='1540' AND aa.deleted_at IS NULL
     ORDER BY aa.id LIMIT 1;

    -- Si V9-Z7 no las pobló, las creamos aquí
    IF v_acct_equipo_compute_id IS NULL THEN
        INSERT INTO accounting_accounts (company_id, custom_name, nature, status, currency_type_id, puc_id, created_at, updated_at)
        SELECT p_company_id, 'Equipo de computacion (1528)', 'DEBIT', 'ACTIVE', v_currency_cop_id, puc.id, NOW(), NOW()
          FROM cfg_chart_of_accounts puc
         WHERE puc.account_code='1528' LIMIT 1
        ON CONFLICT DO NOTHING
        RETURNING id INTO v_acct_equipo_compute_id;

        IF v_acct_equipo_compute_id IS NULL THEN
            SELECT aa.id INTO v_acct_equipo_compute_id
              FROM accounting_accounts aa
              JOIN cfg_chart_of_accounts puc ON puc.id = aa.puc_id
             WHERE aa.company_id=p_company_id AND puc.account_code='1528' LIMIT 1;
        END IF;
    END IF;

    -- --------------------------------------------------------------------- --
    -- 1) USUARIOS — admin, contador, auditor
    -- --------------------------------------------------------------------- --
    INSERT INTO users (name, lastname, username, email, password, status, failed_login_attempts, company_id, created_at, updated_at)
    VALUES
      ('Admin'   , 'QA' || p_suffix, 'admin.qa'    || p_suffix, 'admin@empresa'    || p_suffix || '.test', v_pwd_hash, 'ACTIVE', 0, p_company_id, NOW(), NOW()),
      ('Contador', 'QA' || p_suffix, 'contador.qa' || p_suffix, 'contador@empresa' || p_suffix || '.test', v_pwd_hash, 'ACTIVE', 0, p_company_id, NOW(), NOW()),
      ('Auditor' , 'QA' || p_suffix, 'auditor.qa'  || p_suffix, 'auditor@empresa'  || p_suffix || '.test', v_pwd_hash, 'ACTIVE', 0, p_company_id, NOW(), NOW())
    ON CONFLICT DO NOTHING;

    SELECT id INTO v_admin_id    FROM users WHERE email='admin@empresa'    || p_suffix || '.test' LIMIT 1;
    SELECT id INTO v_contador_id FROM users WHERE email='contador@empresa' || p_suffix || '.test' LIMIT 1;
    SELECT id INTO v_auditor_id  FROM users WHERE email='auditor@empresa'  || p_suffix || '.test' LIMIT 1;

    INSERT INTO users_roles (user_id, role_id) VALUES
      (v_admin_id   , v_admin_role_id),
      (v_contador_id, v_contador_role_id),
      (v_auditor_id , v_auditor_role_id)
    ON CONFLICT DO NOTHING;

    -- --------------------------------------------------------------------- --
    -- 2) TERCEROS — 4 clientes + 4 proveedores con NITs realistas
    -- --------------------------------------------------------------------- --
    -- Clientes
    INSERT INTO third_parties (company_id, third_party_code, business_name, nit, dv, status_id,
                               type_organization_id, type_regimen_id, municipality_id, credit_limit, payment_terms,
                               source, created_at, updated_at)
    VALUES
      (p_company_id, 'CLI-QA' || p_suffix || '-001', 'AGRICOLA DEL CARIBE SAS'        , '9000' || LPAD((1000+p_suffix)::TEXT, 6, '0'), '7', v_status_active_id, v_typeorg_juridica_id, v_typereg_responsable_id, v_municipality_bog_id, 50000000, '30 dias', 'MANUAL', NOW(), NOW()),
      (p_company_id, 'CLI-QA' || p_suffix || '-002', 'COMERCIALIZADORA DEL VALLE LTDA', '9010' || LPAD((1000+p_suffix)::TEXT, 6, '0'), '4', v_status_active_id, v_typeorg_juridica_id, v_typereg_responsable_id, v_municipality_bog_id, 30000000, '15 dias', 'MANUAL', NOW(), NOW()),
      (p_company_id, 'CLI-QA' || p_suffix || '-003', 'DISTRIBUCIONES NACIONALES SAS'  , '9020' || LPAD((1000+p_suffix)::TEXT, 6, '0'), '1', v_status_active_id, v_typeorg_juridica_id, v_typereg_responsable_id, v_municipality_bog_id, 100000000, '60 dias', 'MANUAL', NOW(), NOW()),
      (p_company_id, 'CLI-QA' || p_suffix || '-004', 'SERVICIOS LOGISTICOS COLOMBIA SA', '9030' || LPAD((1000+p_suffix)::TEXT, 6, '0'), '8', v_status_active_id, v_typeorg_juridica_id, v_typereg_responsable_id, v_municipality_bog_id, 75000000, '45 dias', 'MANUAL', NOW(), NOW())
    ON CONFLICT DO NOTHING;

    -- Proveedores
    INSERT INTO third_parties (company_id, third_party_code, business_name, nit, dv, status_id,
                               type_organization_id, type_regimen_id, municipality_id, credit_limit, payment_terms,
                               source, created_at, updated_at)
    VALUES
      (p_company_id, 'PROV-QA' || p_suffix || '-001', 'INSUMOS AGRICOLAS DEL SUR LTDA' , '8110' || LPAD((1000+p_suffix)::TEXT, 6, '0'), '5', v_status_active_id, v_typeorg_juridica_id, v_typereg_responsable_id, v_municipality_bog_id, 0, '30 dias', 'MANUAL', NOW(), NOW()),
      (p_company_id, 'PROV-QA' || p_suffix || '-002', 'TECNOLOGIA EMPRESARIAL SAS'     , '8120' || LPAD((1000+p_suffix)::TEXT, 6, '0'), '2', v_status_active_id, v_typeorg_juridica_id, v_typereg_responsable_id, v_municipality_bog_id, 0, '60 dias', 'MANUAL', NOW(), NOW()),
      (p_company_id, 'PROV-QA' || p_suffix || '-003', 'PAPELERIA Y SUMINISTROS SAS'    , '8130' || LPAD((1000+p_suffix)::TEXT, 6, '0'), '9', v_status_active_id, v_typeorg_juridica_id, v_typereg_responsable_id, v_municipality_bog_id, 0, '15 dias', 'MANUAL', NOW(), NOW()),
      (p_company_id, 'PROV-QA' || p_suffix || '-004', 'SERVICIOS PROFESIONALES JURIDICOS', '8140' || LPAD((1000+p_suffix)::TEXT, 6, '0'), '6', v_status_active_id, v_typeorg_juridica_id, v_typereg_responsable_id, v_municipality_bog_id, 0, '30 dias', 'MANUAL', NOW(), NOW())
    ON CONFLICT DO NOTHING;

    SELECT id INTO v_cliente1_id   FROM third_parties WHERE company_id=p_company_id AND third_party_code='CLI-QA'  || p_suffix || '-001' LIMIT 1;
    SELECT id INTO v_cliente2_id   FROM third_parties WHERE company_id=p_company_id AND third_party_code='CLI-QA'  || p_suffix || '-002' LIMIT 1;
    SELECT id INTO v_cliente3_id   FROM third_parties WHERE company_id=p_company_id AND third_party_code='CLI-QA'  || p_suffix || '-003' LIMIT 1;
    SELECT id INTO v_cliente4_id   FROM third_parties WHERE company_id=p_company_id AND third_party_code='CLI-QA'  || p_suffix || '-004' LIMIT 1;
    SELECT id INTO v_proveedor1_id FROM third_parties WHERE company_id=p_company_id AND third_party_code='PROV-QA' || p_suffix || '-001' LIMIT 1;
    SELECT id INTO v_proveedor2_id FROM third_parties WHERE company_id=p_company_id AND third_party_code='PROV-QA' || p_suffix || '-002' LIMIT 1;
    SELECT id INTO v_proveedor3_id FROM third_parties WHERE company_id=p_company_id AND third_party_code='PROV-QA' || p_suffix || '-003' LIMIT 1;
    SELECT id INTO v_proveedor4_id FROM third_parties WHERE company_id=p_company_id AND third_party_code='PROV-QA' || p_suffix || '-004' LIMIT 1;

    -- 2.5) Asignar roles CLIENTE / PROVEEDOR (HU-TER E2: tercero requiere al menos un rol activo)
    DECLARE
        v_role_cli BIGINT;
        v_role_prov BIGINT;
    BEGIN
        SELECT id INTO v_role_cli  FROM third_party_role_catalog WHERE name='CLIENTE'   AND deleted_at IS NULL LIMIT 1;
        SELECT id INTO v_role_prov FROM third_party_role_catalog WHERE name='PROVEEDOR' AND deleted_at IS NULL LIMIT 1;
        IF v_role_cli IS NOT NULL THEN
            INSERT INTO third_party_role_assignments_v2 (company_id, third_party_id, role_id, valid_from, valid_to, created_at)
            VALUES (p_company_id, v_cliente1_id, v_role_cli, v_today - INTERVAL '60 days', NULL, NOW()),
                   (p_company_id, v_cliente2_id, v_role_cli, v_today - INTERVAL '60 days', NULL, NOW()),
                   (p_company_id, v_cliente3_id, v_role_cli, v_today - INTERVAL '60 days', NULL, NOW()),
                   (p_company_id, v_cliente4_id, v_role_cli, v_today - INTERVAL '60 days', NULL, NOW())
            ON CONFLICT DO NOTHING;
        END IF;
        IF v_role_prov IS NOT NULL THEN
            INSERT INTO third_party_role_assignments_v2 (company_id, third_party_id, role_id, valid_from, valid_to, created_at)
            VALUES (p_company_id, v_proveedor1_id, v_role_prov, v_today - INTERVAL '60 days', NULL, NOW()),
                   (p_company_id, v_proveedor2_id, v_role_prov, v_today - INTERVAL '60 days', NULL, NOW()),
                   (p_company_id, v_proveedor3_id, v_role_prov, v_today - INTERVAL '60 days', NULL, NOW()),
                   (p_company_id, v_proveedor4_id, v_role_prov, v_today - INTERVAL '60 days', NULL, NOW())
            ON CONFLICT DO NOTHING;
        END IF;
        -- v1 (ThirdParty.roles @ManyToMany): el frontend lee de aqui
        IF v_role_cli IS NOT NULL THEN
            INSERT INTO third_party_role_assignments (third_party_id, role_id) VALUES
              (v_cliente1_id, v_role_cli),(v_cliente2_id, v_role_cli),
              (v_cliente3_id, v_role_cli),(v_cliente4_id, v_role_cli)
            ON CONFLICT DO NOTHING;
        END IF;
        IF v_role_prov IS NOT NULL THEN
            INSERT INTO third_party_role_assignments (third_party_id, role_id) VALUES
              (v_proveedor1_id, v_role_prov),(v_proveedor2_id, v_role_prov),
              (v_proveedor3_id, v_role_prov),(v_proveedor4_id, v_role_prov)
            ON CONFLICT DO NOTHING;
        END IF;
    END;

    -- --------------------------------------------------------------------- --
    -- 3) BANCO + SUCURSAL + CUENTA + CAJA
    -- --------------------------------------------------------------------- --
    INSERT INTO banks (company_id, code, code_ach, name, name_short, nit, type_bank, swift, country_id, status, created_at, updated_at)
    VALUES (p_company_id, 'BC-QA' || p_suffix, 'ACH-QA' || p_suffix, 'BANCOLOMBIA QA' || p_suffix,
            'BC-QA' || p_suffix, '890903938' || p_suffix, 'COMMERCIAL', 'COLOC', 1, 'ACTIVE', NOW(), NOW())
    ON CONFLICT DO NOTHING;

    SELECT id INTO v_bank_id FROM banks WHERE company_id=p_company_id AND code='BC-QA' || p_suffix LIMIT 1;

    INSERT INTO bank_branches (company_id, bank_id, address, main_branch, municipality_id, created_at, updated_at)
    VALUES (p_company_id, v_bank_id, 'Calle 100 # 50-' || (10 + p_suffix), TRUE, v_municipality_bog_id, NOW(), NOW())
    ON CONFLICT DO NOTHING;

    SELECT id INTO v_branch_id FROM bank_branches WHERE company_id=p_company_id AND bank_id=v_bank_id LIMIT 1;

    INSERT INTO bank_accounts (company_id, code, account_name, account_number, account_type,
                                allows_overdraft, handles_checkbook, initial_balance, notify_low_balance,
                                opening_date, status, accounting_account_id, bank_id, bank_branch_id, currency_type_id,
                                created_at, updated_at)
    SELECT p_company_id, 'CTA-QA' || p_suffix, 'Cuenta Corriente Operativa QA' || p_suffix,
           '12345' || LPAD(p_suffix::TEXT, 6, '0'), 'CORRIENTE',
           FALSE, TRUE, 25000000, FALSE, v_today - INTERVAL '90 days', 'ACTIVA',
           v_acct_bancos_id, v_bank_id, v_branch_id, v_currency_cop_id,
           NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM bank_accounts WHERE company_id=p_company_id AND code='CTA-QA' || p_suffix);

    SELECT id INTO v_bank_acct_id FROM bank_accounts WHERE company_id=p_company_id AND code='CTA-QA' || p_suffix LIMIT 1;

    INSERT INTO cash (company_id, cash_code, cash_name, cash_type, cash_status, accounting_book,
                      audit_frequency, physical_location, requires_authorization, initial_balance,
                      current_balance, initial_balance_date, cash_creation_date, accounting_account_id,
                      currency_id, principal_responsible_id, created_at, updated_at)
    SELECT p_company_id, 'CAJA-QA' || p_suffix, 'Caja Menor QA' || p_suffix, 'PETTY_CASH', 'ACTIVE',
           'LOCAL', 'WEEKLY', 'Oficina Principal', FALSE, 1000000, 1000000,
           v_today - INTERVAL '60 days', v_today - INTERVAL '60 days', v_acct_caja_id,
           v_currency_cop_id, v_admin_id, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM cash WHERE company_id=p_company_id AND cash_code='CAJA-QA' || p_suffix);

    SELECT id INTO v_cash_id FROM cash WHERE company_id=p_company_id AND cash_code='CAJA-QA' || p_suffix LIMIT 1;

    -- --------------------------------------------------------------------- --
    -- 4) CHEQUERA + 5 CHEQUES
    -- --------------------------------------------------------------------- --
    INSERT INTO checkbooks (company_id, bank_account_id, checkbook_number, check_start_number, check_end_number,
                            total_checks, used_checks, available_checks, status, issuing_bank, activation_date, created_at, updated_at)
    SELECT p_company_id, v_bank_acct_id, 'CHK-QA' || p_suffix || '-001', 1001, 1100, 100, 3, 97, 'ACTIVA',
           'BANCOLOMBIA QA' || p_suffix, v_today - INTERVAL '30 days', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM checkbooks WHERE company_id=p_company_id AND checkbook_number='CHK-QA' || p_suffix || '-001');

    SELECT id INTO v_checkbook_id FROM checkbooks WHERE company_id=p_company_id AND checkbook_number='CHK-QA' || p_suffix || '-001' LIMIT 1;

    INSERT INTO checks (company_id, checkbooks_id, number_check, beneficiary, concept, issue_date,
                        value, type_check, status_check, block_payment, created_at, updated_at)
    VALUES
      (p_company_id, v_checkbook_id, 1001, 'Pago proveedor 1'   , 'Insumos abril' , v_today - INTERVAL '20 days', 500000  , 'FISICO', 'EMITIDO', FALSE, NOW(), NOW()),
      (p_company_id, v_checkbook_id, 1002, 'Pago proveedor 2'   , 'Tecnologia'    , v_today - INTERVAL '15 days', 1200000 , 'FISICO', 'EMITIDO', FALSE, NOW(), NOW()),
      (p_company_id, v_checkbook_id, 1003, 'Pago servicios'     , 'Honorarios'    , v_today - INTERVAL '10 days', 800000  , 'FISICO', 'COBRADO', FALSE, NOW(), NOW()),
      (p_company_id, v_checkbook_id, 1004, 'Disponible 1'       , 'En blanco'     , v_today                     , 1       , 'FISICO', 'EMITIDO', FALSE, NOW(), NOW()),
      (p_company_id, v_checkbook_id, 1005, 'Disponible 2'       , 'En blanco'     , v_today                     , 1       , 'FISICO', 'EMITIDO', FALSE, NOW(), NOW())
    ON CONFLICT DO NOTHING;

    -- --------------------------------------------------------------------- --
    -- 5) MOVIMIENTOS FINANCIEROS — 5 movimientos de banco
    -- --------------------------------------------------------------------- --
    INSERT INTO financial_movements (company_id, bank_account_id, movement_date, source_type, amount,
                                      description, flow_activity, created_at, updated_at)
    VALUES
      (p_company_id, v_bank_acct_id, v_today - INTERVAL '25 days', 'MANUAL', 5000000 , 'Deposito inicial QA'        , 'OPERATIVA', NOW(), NOW()),
      (p_company_id, v_bank_acct_id, v_today - INTERVAL '20 days', 'MANUAL',  500000 , 'Retiro pago proveedor 1'    , 'OPERATIVA', NOW(), NOW()),
      (p_company_id, v_bank_acct_id, v_today - INTERVAL '15 days', 'MANUAL', 1200000 , 'Retiro pago proveedor 2'    , 'OPERATIVA', NOW(), NOW()),
      (p_company_id, v_bank_acct_id, v_today - INTERVAL '10 days', 'MANUAL', 3500000 , 'Deposito cobro cliente'     , 'OPERATIVA', NOW(), NOW()),
      (p_company_id, v_bank_acct_id, v_today - INTERVAL '5 days' , 'MANUAL',  800000 , 'Retiro servicios honorarios', 'OPERATIVA', NOW(), NOW())
    ON CONFLICT DO NOTHING;

    -- --------------------------------------------------------------------- --
    -- 6) RESOLUCION DIAN ACTIVA
    -- --------------------------------------------------------------------- --
    INSERT INTO dian_resolutions (company_id, resolution_number, prefix, start_date, end_date,
                                   start_number, end_number, current_number, status, created_at, updated_at)
    SELECT p_company_id, 'DIAN-QA' || p_suffix || '-2026', 'FV', v_today - INTERVAL '60 days', v_today + INTERVAL '300 days',
           1, 5000, 1, 'ACTIVE', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM dian_resolutions WHERE company_id=p_company_id AND resolution_number='DIAN-QA' || p_suffix || '-2026');

    -- --------------------------------------------------------------------- --
    -- 7) FACTURAS DE VENTA + COBROS + JE
    -- --------------------------------------------------------------------- --
    -- FV1: PAID
    INSERT INTO sales_invoices (company_id, third_party_id, invoice_number, invoice_date, due_date,
                                 subtotal, total_tax, total_withholding, total_amount, balance_due,
                                 status, exchange_rate, xml_sent, currency_id, created_at, updated_at)
    SELECT p_company_id, v_cliente1_id, 'FV-' || v_year || LPAD((p_suffix*1000 + 1)::TEXT, 6, '0'),
           v_today - INTERVAL '30 days', v_today - INTERVAL '15 days',
           1000000, 0, 0, 1000000, 0, 'PAID', 1, FALSE, v_currency_cop_id, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM sales_invoices WHERE company_id=p_company_id
                       AND invoice_number='FV-' || v_year || LPAD((p_suffix*1000 + 1)::TEXT, 6, '0'));

    SELECT id INTO v_fv_id FROM sales_invoices WHERE company_id=p_company_id
       AND invoice_number='FV-' || v_year || LPAD((p_suffix*1000 + 1)::TEXT, 6, '0') LIMIT 1;

    INSERT INTO sales_invoice_lines (company_id, invoice_id, description, quantity, unit_price,
                                      discount, subtotal, tax_amount, withholding_amount, total, created_at, updated_at)
    SELECT p_company_id, v_fv_id, 'Venta producto agricola', 10, 100000, 0, 1000000, 0, 0, 1000000, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM sales_invoice_lines WHERE invoice_id=v_fv_id);

    -- Cobro completo de FV1
    INSERT INTO ar_payments (company_id, invoice_id, payment_date, amount, payment_method, status,
                              bank_account_id, payment_reference, source, created_at, updated_at)
    SELECT p_company_id, v_fv_id, v_today - INTERVAL '10 days', 1000000, 'TRANSFER', 'COMPLETED',
           v_bank_acct_id, 'COBRO-QA' || p_suffix || '-001', 'MANUAL', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM ar_payments WHERE company_id=p_company_id
                       AND payment_reference='COBRO-QA' || p_suffix || '-001');

    -- FV2: PARTIALLY_PAID
    INSERT INTO sales_invoices (company_id, third_party_id, invoice_number, invoice_date, due_date,
                                 subtotal, total_tax, total_withholding, total_amount, balance_due,
                                 status, exchange_rate, xml_sent, currency_id, created_at, updated_at)
    SELECT p_company_id, v_cliente2_id, 'FV-' || v_year || LPAD((p_suffix*1000 + 2)::TEXT, 6, '0'),
           v_today - INTERVAL '20 days', v_today + INTERVAL '10 days',
           2500000, 475000, 0, 2975000, 1500000, 'PARTIALLY_PAID', 1, FALSE, v_currency_cop_id, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM sales_invoices WHERE company_id=p_company_id
                       AND invoice_number='FV-' || v_year || LPAD((p_suffix*1000 + 2)::TEXT, 6, '0'));

    -- FV3: ISSUED (sin cobros)
    INSERT INTO sales_invoices (company_id, third_party_id, invoice_number, invoice_date, due_date,
                                 subtotal, total_tax, total_withholding, total_amount, balance_due,
                                 status, exchange_rate, xml_sent, currency_id, created_at, updated_at)
    SELECT p_company_id, v_cliente3_id, 'FV-' || v_year || LPAD((p_suffix*1000 + 3)::TEXT, 6, '0'),
           v_today - INTERVAL '5 days', v_today + INTERVAL '25 days',
           3000000, 570000, 75000, 3495000, 3495000, 'ISSUED', 1, FALSE, v_currency_cop_id, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM sales_invoices WHERE company_id=p_company_id
                       AND invoice_number='FV-' || v_year || LPAD((p_suffix*1000 + 3)::TEXT, 6, '0'));

    -- --------------------------------------------------------------------- --
    -- 8) FACTURAS DE COMPRA + PAGOS
    -- --------------------------------------------------------------------- --
    -- FC1: PAID
    INSERT INTO invoices (company_id, third_party_id, type_invoice_id, invoice_state_id, payment_forms_id, user_id,
                           resolution, resolution_invoice, supplier_invoice_number, invoice_date, invoice_due_day,
                           total_payment, total_tax, total_discount, total_amount, balance_due,
                           invoice_status, source, notes, created_at, updated_at)
    SELECT p_company_id, v_proveedor1_id, v_typeinv_fc_id, v_invstate_pending_id, v_payment_form_credito_id, v_admin_id,
           'FC-QA' || p_suffix || '-001', 'FC-QA' || p_suffix || '-001', 'PROV-FC-' || p_suffix || '-001',
           v_today - INTERVAL '25 days', 15,
           500000, 95000, 0, 595000, 0, 'PAID', 'MANUAL', 'Factura compra QA seed', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM invoices WHERE company_id=p_company_id AND resolution_invoice='FC-QA' || p_suffix || '-001');

    SELECT id INTO v_fc_id FROM invoices WHERE company_id=p_company_id AND resolution_invoice='FC-QA' || p_suffix || '-001' LIMIT 1;

    INSERT INTO ap_payments (company_id, invoice_id, payment_date, amount, payment_method, status,
                              bank_account_id, payment_reference, source, created_at, updated_at)
    SELECT p_company_id, v_fc_id, v_today - INTERVAL '20 days', 595000, 'TRANSFER', 'CONFIRMED',
           v_bank_acct_id, 'PAGO-QA' || p_suffix || '-001', 'MANUAL', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM ap_payments WHERE company_id=p_company_id
                       AND payment_reference='PAGO-QA' || p_suffix || '-001');

    -- FC2: PARTIALLY_PAID
    INSERT INTO invoices (company_id, third_party_id, type_invoice_id, invoice_state_id, payment_forms_id, user_id,
                           resolution, resolution_invoice, supplier_invoice_number, invoice_date, invoice_due_day,
                           total_payment, total_tax, total_discount, total_amount, balance_due,
                           invoice_status, source, notes, created_at, updated_at)
    SELECT p_company_id, v_proveedor2_id, v_typeinv_fc_id, v_invstate_pending_id, v_payment_form_credito_id, v_admin_id,
           'FC-QA' || p_suffix || '-002', 'FC-QA' || p_suffix || '-002', 'PROV-FC-' || p_suffix || '-002',
           v_today - INTERVAL '15 days', 20,
           1200000, 228000, 0, 1428000, 700000, 'PARTIALLY_PAID', 'MANUAL', 'Factura tecnologia QA', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM invoices WHERE company_id=p_company_id AND resolution_invoice='FC-QA' || p_suffix || '-002');

    -- FC3: PENDING
    INSERT INTO invoices (company_id, third_party_id, type_invoice_id, invoice_state_id, payment_forms_id, user_id,
                           resolution, resolution_invoice, supplier_invoice_number, invoice_date, invoice_due_day,
                           total_payment, total_tax, total_discount, total_amount, balance_due,
                           invoice_status, source, notes, created_at, updated_at)
    SELECT p_company_id, v_proveedor3_id, v_typeinv_fc_id, v_invstate_pending_id, v_payment_form_credito_id, v_admin_id,
           'FC-QA' || p_suffix || '-003', 'FC-QA' || p_suffix || '-003', 'PROV-FC-' || p_suffix || '-003',
           v_today - INTERVAL '10 days', 25,
           300000, 57000, 0, 357000, 357000, 'PENDING', 'MANUAL', 'Factura papeleria QA', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM invoices WHERE company_id=p_company_id AND resolution_invoice='FC-QA' || p_suffix || '-003');

    -- --------------------------------------------------------------------- --
    -- 9) REGLAS DE DEPRECIACION (necesarias para crear activos)
    -- --------------------------------------------------------------------- --
    -- Asegurar que existen las 3 cuentas PPE (oficina/compute/flota) antes de
    -- crear las reglas de depreciacion. Si alguna es null, la creamos.
    IF v_acct_equipo_oficina_id IS NULL THEN
        INSERT INTO accounting_accounts (company_id, custom_name, nature, status, currency_type_id, puc_id, created_at, updated_at)
        SELECT p_company_id, 'Equipo de oficina (1524)', 'DEBIT', 'ACTIVE', v_currency_cop_id, puc.id, NOW(), NOW()
          FROM cfg_chart_of_accounts puc WHERE puc.account_code='1524' LIMIT 1
        ON CONFLICT DO NOTHING;
        SELECT aa.id INTO v_acct_equipo_oficina_id FROM accounting_accounts aa
          JOIN cfg_chart_of_accounts puc ON puc.id = aa.puc_id
         WHERE aa.company_id=p_company_id AND puc.account_code='1524' LIMIT 1;
    END IF;
    IF v_acct_flota_id IS NULL THEN
        INSERT INTO accounting_accounts (company_id, custom_name, nature, status, currency_type_id, puc_id, created_at, updated_at)
        SELECT p_company_id, 'Flota y equipo de transporte (1540)', 'DEBIT', 'ACTIVE', v_currency_cop_id, puc.id, NOW(), NOW()
          FROM cfg_chart_of_accounts puc WHERE puc.account_code='1540' LIMIT 1
        ON CONFLICT DO NOTHING;
        SELECT aa.id INTO v_acct_flota_id FROM accounting_accounts aa
          JOIN cfg_chart_of_accounts puc ON puc.id = aa.puc_id
         WHERE aa.company_id=p_company_id AND puc.account_code='1540' LIMIT 1;
    END IF;

    INSERT INTO depretation_rules (company_id, name, depretation_type, depretation_rate,
                                    description_structured, effective_date, residual_value, status,
                                    useful_life_years, accounting_account_id, created_at, updated_at)
    VALUES
      (p_company_id, 'OFICINA QA' || p_suffix    , 'LINEAR', 10.00, 'Equipo de oficina lineal 10 anios', v_today - INTERVAL '180 days', 0, 'ACTIVE', 10, v_acct_equipo_oficina_id, NOW(), NOW()),
      (p_company_id, 'COMPUTACION QA' || p_suffix, 'LINEAR', 20.00, 'Equipo de computacion lineal 5 anios', v_today - INTERVAL '180 days', 0, 'ACTIVE', 5, v_acct_equipo_compute_id, NOW(), NOW()),
      (p_company_id, 'FLOTA QA' || p_suffix      , 'LINEAR', 20.00, 'Flota y equipo de transporte lineal 5 anios', v_today - INTERVAL '180 days', 0, 'ACTIVE', 5, v_acct_flota_id, NOW(), NOW())
    ON CONFLICT DO NOTHING;

    SELECT id INTO v_depr_oficina_id FROM depretation_rules WHERE company_id=p_company_id AND name='OFICINA QA' || p_suffix LIMIT 1;
    SELECT id INTO v_depr_compute_id FROM depretation_rules WHERE company_id=p_company_id AND name='COMPUTACION QA' || p_suffix LIMIT 1;
    SELECT id INTO v_depr_flota_id   FROM depretation_rules WHERE company_id=p_company_id AND name='FLOTA QA' || p_suffix LIMIT 1;

    -- --------------------------------------------------------------------- --
    -- 10) ACTIVOS (3) — computador, vehiculo, escritorio
    -- --------------------------------------------------------------------- --
    INSERT INTO assets (company_id, asset_code, asset_name, asset_type, classification, asset_status,
                         acquisition_date, acquisition_value, current_book_value, useful_life_months,
                         supplier_id, depretation_rule_id, accounting_account_id, description,
                         created_at, updated_at)
    VALUES
      (p_company_id, 'ACT-QA' || p_suffix || '-001', 'Computador Portatil HP QA' || p_suffix    , 'TANGIBLE', 'NON_CURRENT', 'ACTIVE',
       v_today - INTERVAL '120 days', 3500000, 3441666.67, 60, v_proveedor2_id, v_depr_compute_id,
       v_acct_equipo_compute_id, 'Computador para gerencia', NOW(), NOW()),
      (p_company_id, 'ACT-QA' || p_suffix || '-002', 'Camioneta Toyota Hilux QA' || p_suffix    , 'TANGIBLE', 'NON_CURRENT', 'ACTIVE',
       v_today - INTERVAL '180 days', 120000000, 118000000, 60, v_proveedor1_id, v_depr_flota_id,
       COALESCE(v_acct_flota_id, v_acct_equipo_compute_id), 'Vehiculo de transporte de mercancia', NOW(), NOW()),
      (p_company_id, 'ACT-QA' || p_suffix || '-003', 'Escritorio Ejecutivo QA' || p_suffix      , 'TANGIBLE', 'NON_CURRENT', 'ACTIVE',
       v_today - INTERVAL '90 days', 800000, 794444.44, 120, v_proveedor3_id, v_depr_oficina_id,
       COALESCE(v_acct_equipo_oficina_id, v_acct_equipo_compute_id), 'Escritorio para oficina', NOW(), NOW())
    ON CONFLICT DO NOTHING;

    -- --------------------------------------------------------------------- --
    -- 11) EMPLEADOS (4)
    -- --------------------------------------------------------------------- --
    INSERT INTO employees (company_id, document_type, document_number, full_name, position, base_salary,
                            hire_date, contract_type, eps, pension_fund, arl, compensation_box, status,
                            created_at, updated_at)
    VALUES
      (p_company_id, 'CC', 'EMP-QA' || p_suffix || '-001', 'Maria Perez Rodriguez ' || p_suffix , 'Contadora'         , 3500000, v_today - INTERVAL '365 days', 'PERMANENT', 'Sura'   , 'Porvenir', 'Sura', 'Compensar', 'ACTIVE', NOW(), NOW()),
      (p_company_id, 'CC', 'EMP-QA' || p_suffix || '-002', 'Carlos Gomez Mendez ' || p_suffix   , 'Auxiliar Contable' , 2000000, v_today - INTERVAL '180 days', 'PERMANENT', 'Sura'   , 'Porvenir', 'Sura', 'Compensar', 'ACTIVE', NOW(), NOW()),
      (p_company_id, 'CC', 'EMP-QA' || p_suffix || '-003', 'Laura Sanchez Lopez ' || p_suffix   , 'Vendedora'         , 2500000, v_today - INTERVAL '270 days', 'PERMANENT', 'Salud Total', 'Colfondos', 'Sura', 'Compensar', 'ACTIVE', NOW(), NOW()),
      (p_company_id, 'CC', 'EMP-QA' || p_suffix || '-004', 'Pedro Martinez Diaz ' || p_suffix   , 'Mensajero'         , 1500000, v_today - INTERVAL '90 days' , 'PERMANENT', 'Sura'   , 'Porvenir', 'Sura', 'Compensar', 'ACTIVE', NOW(), NOW())
    ON CONFLICT DO NOTHING;

    -- --------------------------------------------------------------------- --
    -- 12) COMPROBANTES CONTABLES (5) - mix DRAFT/POSTED
    -- --------------------------------------------------------------------- --
    -- JE 1: POSTED partida doble simple
    INSERT INTO journal_entries (company_id, entry_number, fiscal_year, entry_date, period_year, period_month,
                                  description, source_module, status, total_debit, total_credit, created_by,
                                  created_at, updated_at)
    SELECT p_company_id,
           COALESCE((SELECT MAX(entry_number) FROM journal_entries WHERE company_id=p_company_id AND fiscal_year=v_year), 0) + 1,
           v_year, v_today - INTERVAL '30 days', EXTRACT(YEAR FROM v_today - INTERVAL '30 days')::INT,
           EXTRACT(MONTH FROM v_today - INTERVAL '30 days')::INT,
           'Asiento apertura QA seed', 'CG', 'POSTED', 5000000, 5000000, 'admin@empresa' || p_suffix || '.test',
           NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM journal_entries WHERE company_id=p_company_id AND description='Asiento apertura QA seed');

    SELECT id INTO v_je_id FROM journal_entries WHERE company_id=p_company_id AND description='Asiento apertura QA seed' LIMIT 1;

    INSERT INTO journal_entry_lines (company_id, journal_entry_id, line_order, accounting_account_id, debit_amount, credit_amount, description, created_at)
    SELECT p_company_id, v_je_id, 1, v_acct_bancos_id, 5000000, 0, 'Apertura banco', NOW()
    WHERE NOT EXISTS (SELECT 1 FROM journal_entry_lines WHERE journal_entry_id=v_je_id AND line_order=1);
    INSERT INTO journal_entry_lines (company_id, journal_entry_id, line_order, accounting_account_id, debit_amount, credit_amount, description, created_at)
    SELECT p_company_id, v_je_id, 2, v_acct_ingresos_id, 0, 5000000, 'Apertura ingresos', NOW()
    WHERE NOT EXISTS (SELECT 1 FROM journal_entry_lines WHERE journal_entry_id=v_je_id AND line_order=2);

    -- JE 2: DRAFT
    INSERT INTO journal_entries (company_id, entry_number, fiscal_year, entry_date, period_year, period_month,
                                  description, source_module, status, total_debit, total_credit, created_by,
                                  created_at, updated_at)
    SELECT p_company_id,
           COALESCE((SELECT MAX(entry_number) FROM journal_entries WHERE company_id=p_company_id AND fiscal_year=v_year), 0) + 1,
           v_year, v_today, EXTRACT(YEAR FROM v_today)::INT, EXTRACT(MONTH FROM v_today)::INT,
           'Comprobante en borrador para QA', 'CG', 'DRAFT', 250000, 250000, 'admin@empresa' || p_suffix || '.test',
           NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM journal_entries WHERE company_id=p_company_id AND description='Comprobante en borrador para QA');

    SELECT id INTO v_je_id FROM journal_entries WHERE company_id=p_company_id AND description='Comprobante en borrador para QA' LIMIT 1;

    INSERT INTO journal_entry_lines (company_id, journal_entry_id, line_order, accounting_account_id, debit_amount, credit_amount, description, created_at)
    SELECT p_company_id, v_je_id, 1, v_acct_caja_id, 250000, 0, 'Caja menor', NOW()
    WHERE NOT EXISTS (SELECT 1 FROM journal_entry_lines WHERE journal_entry_id=v_je_id AND line_order=1);
    INSERT INTO journal_entry_lines (company_id, journal_entry_id, line_order, accounting_account_id, debit_amount, credit_amount, description, created_at)
    SELECT p_company_id, v_je_id, 2, v_acct_bancos_id, 0, 250000, 'Salida banco', NOW()
    WHERE NOT EXISTS (SELECT 1 FROM journal_entry_lines WHERE journal_entry_id=v_je_id AND line_order=2);

    RAISE NOTICE '_qa_seed_company: TERMINADO empresa id=%', p_company_id;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- PASO 2 — Crear las 6 empresas QA y sembrarlas
-- =============================================================================
DO $$
DECLARE
    v_suffix INT;
    v_company_id BIGINT;
    v_existing_id BIGINT;
    v_year INT := EXTRACT(YEAR FROM CURRENT_DATE)::INT;
    v_company_nit TEXT;
    v_company_name TEXT;
BEGIN
    FOR v_suffix IN 1..6 LOOP
        v_company_nit  := '90' || LPAD((1100000 + v_suffix*111111)::TEXT, 8, '0');
        v_company_name := 'EMPRESA QA ' || v_suffix || ' SAS';

        -- Si ya existe (re-corrida), reutilizar el id
        SELECT id INTO v_existing_id FROM companies WHERE nit=v_company_nit AND deleted_at IS NULL LIMIT 1;

        IF v_existing_id IS NULL THEN
            INSERT INTO companies (nit, dv, business_name, status,
                                    legal_representative, email, phone, address,
                                    created_at, updated_at)
            VALUES (v_company_nit, '0', v_company_name, 'ACTIVE',
                    'Admin QA ' || v_suffix,
                    'admin@empresa' || v_suffix || '.test',
                    '+57 30' || v_suffix || ' 555 5555',
                    'Calle 100 #50-' || (10 + v_suffix) || ', Bogota',
                    NOW(), NOW())
            RETURNING id INTO v_company_id;

            -- Auto-provision: 12 periodos + 19 mappings + 16 NOM concepts + 20 params + 1 CC default
            PERFORM _tenant_auto_provision(v_company_id, v_year);
            -- Cuentas PPE clase 15xx (V9-Z7 lo hace para activas, lo invocamos por seguridad)
            -- (V9-Z7 corre por loop sobre TODAS las activas, asi que esta llamada es opcional)
        ELSE
            v_company_id := v_existing_id;
            RAISE NOTICE 'Empresa % ya existe con id=%, reusando', v_company_name, v_company_id;
        END IF;

        -- Sembrar datos operativos
        PERFORM _qa_seed_company(v_company_id, v_suffix);
    END LOOP;
END $$;

-- =============================================================================
-- PASO 3 — Reaplicar V9-Z7 (cuentas PPE) a las nuevas empresas
-- =============================================================================
DO $$
DECLARE
    v_company RECORD;
    v_currency_cop_id BIGINT;
    v_puc RECORD;
BEGIN
    SELECT id INTO v_currency_cop_id FROM cfg_currency_types WHERE iso_code='COP' LIMIT 1;

    FOR v_company IN SELECT id FROM companies WHERE deleted_at IS NULL AND status='ACTIVE' AND id > 6 LOOP
        FOR v_puc IN SELECT id, account_code, account_name FROM cfg_chart_of_accounts
                      WHERE account_code IN ('1504','1516','1520','1524','1528','1532','1540','1560')
                        AND deleted_at IS NULL LOOP
            INSERT INTO accounting_accounts (company_id, custom_name, nature, status, currency_type_id, puc_id, created_at, updated_at)
            VALUES (v_company.id, v_puc.account_name || ' (' || v_puc.account_code || ')',
                    'DEBIT', 'ACTIVE', v_currency_cop_id, v_puc.id, NOW(), NOW())
            ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;
END $$;

-- Limpieza opcional: dropear la función ayudante (no la dejamos viva en BD prod)
-- DROP FUNCTION IF EXISTS _qa_seed_company(BIGINT, INT);
