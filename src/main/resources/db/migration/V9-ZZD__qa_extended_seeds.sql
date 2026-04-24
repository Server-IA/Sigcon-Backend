-- V9-ZZD: Seeds extendidos para las 6 empresas QA (id 4..9).
-- Llena los submodulos que V9-ZZC dejo vacios:
--   exchange_rates, ruler_tax, risk_segmentation, commercial_data,
--   bnk_cash_flow_projections, cash_audits, ap_advances,
--   ap_credit_debit_notes, purchase_orders + lines, goods_receipts,
--   ar_advances, ar_credit_debit_notes, payroll_receipts (mes actual).
-- Idempotente: usa WHERE NOT EXISTS por clave de negocio (suffix por empresa).

-- Replace UNIQUE global por compuesto multi-tenant (multi-empresa pueden tener mismo nombre)
DO $$
BEGIN
    IF EXISTS(SELECT 1 FROM pg_indexes WHERE indexname='uidx_bnk_cfp_name_active') THEN
        DROP INDEX IF EXISTS uidx_bnk_cfp_name_active;
    END IF;
    CREATE UNIQUE INDEX IF NOT EXISTS uk_bnk_cfp_company_name_active
        ON bnk_cash_flow_projections (company_id, name)
        WHERE deleted_at IS NULL;
END $$;

CREATE OR REPLACE FUNCTION _qa_ext_seed_company(p_company_id BIGINT, p_suffix INT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_today               DATE := CURRENT_DATE;
    v_year                INT  := EXTRACT(YEAR FROM CURRENT_DATE)::INT;
    v_month               INT  := EXTRACT(MONTH FROM CURRENT_DATE)::INT;
    v_admin_id            BIGINT;
    v_currency_cop_id     BIGINT;
    v_currency_usd_id     BIGINT;
    v_payment_term_id     BIGINT;
    v_acct_clientes_id    BIGINT;
    v_acct_proveedores_id BIGINT;
    v_acct_ingresos_id    BIGINT;
    v_acct_iva_id         BIGINT;
    v_acct_gastos_id      BIGINT;
    v_acct_caja_id        BIGINT;
    v_acct_bancos_id      BIGINT;
    v_cash_id             BIGINT;
    v_bank_acct_id        BIGINT;
    v_cliente1_id         BIGINT;
    v_cliente2_id         BIGINT;
    v_proveedor1_id       BIGINT;
    v_proveedor2_id       BIGINT;
    v_fv_id               BIGINT;
    v_fc_id               BIGINT;
    v_po_id               BIGINT;
    v_emp_id              BIGINT;
    v_concept_basic_id    BIGINT;
    v_concept_health_id   BIGINT;
    v_concept_pension_id  BIGINT;
    v_concept_transp_id   BIGINT;
    v_receipt_id          BIGINT;
BEGIN
    RAISE NOTICE '_qa_ext_seed_company: empresa=% suffix=%', p_company_id, p_suffix;

    -- Lookups base
    SELECT u.id INTO v_admin_id FROM users u
     WHERE u.company_id = p_company_id AND u.email = 'admin@empresa' || p_suffix || '.test'
       AND u.deleted_at IS NULL LIMIT 1;
    IF v_admin_id IS NULL THEN
        SELECT u.id INTO v_admin_id FROM users u
         WHERE u.company_id = p_company_id AND u.deleted_at IS NULL ORDER BY u.id LIMIT 1;
    END IF;

    SELECT id INTO v_currency_cop_id FROM cfg_currency_types WHERE iso_code = 'COP' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_currency_usd_id FROM cfg_currency_types WHERE iso_code = 'USD' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_payment_term_id FROM payment_forms WHERE deleted_at IS NULL ORDER BY id LIMIT 1;

    SELECT accounting_account_id INTO v_acct_clientes_id    FROM account_mappings WHERE company_id=p_company_id AND concept_code='AR_CLIENTES'         LIMIT 1;
    SELECT accounting_account_id INTO v_acct_proveedores_id FROM account_mappings WHERE company_id=p_company_id AND concept_code='AP_PROVEEDORES'      LIMIT 1;
    SELECT accounting_account_id INTO v_acct_ingresos_id    FROM account_mappings WHERE company_id=p_company_id AND concept_code='AR_INGRESOS'         LIMIT 1;
    SELECT accounting_account_id INTO v_acct_iva_id         FROM account_mappings WHERE company_id=p_company_id AND concept_code='AP_IVA_DESCONTABLE'  LIMIT 1;
    SELECT accounting_account_id INTO v_acct_gastos_id      FROM account_mappings WHERE company_id=p_company_id AND concept_code='AP_COMPRAS_DEFAULT'  LIMIT 1;
    SELECT accounting_account_id INTO v_acct_caja_id        FROM account_mappings WHERE company_id=p_company_id AND concept_code='CAJA_DEFAULT'        LIMIT 1;
    SELECT accounting_account_id INTO v_acct_bancos_id      FROM account_mappings WHERE company_id=p_company_id AND concept_code='BANCOS_DEFAULT'      LIMIT 1;

    SELECT id INTO v_cash_id      FROM cash          WHERE company_id=p_company_id ORDER BY id LIMIT 1;
    SELECT id INTO v_bank_acct_id FROM bank_accounts WHERE company_id=p_company_id ORDER BY id LIMIT 1;

    SELECT id INTO v_cliente1_id   FROM third_parties WHERE company_id=p_company_id AND third_party_code='CLI-QA'  || p_suffix || '-001' LIMIT 1;
    SELECT id INTO v_cliente2_id   FROM third_parties WHERE company_id=p_company_id AND third_party_code='CLI-QA'  || p_suffix || '-002' LIMIT 1;
    SELECT id INTO v_proveedor1_id FROM third_parties WHERE company_id=p_company_id AND third_party_code='PROV-QA' || p_suffix || '-001' LIMIT 1;
    SELECT id INTO v_proveedor2_id FROM third_parties WHERE company_id=p_company_id AND third_party_code='PROV-QA' || p_suffix || '-002' LIMIT 1;

    SELECT id INTO v_fv_id FROM sales_invoices WHERE company_id=p_company_id ORDER BY id LIMIT 1;
    SELECT id INTO v_fc_id FROM invoices       WHERE company_id=p_company_id ORDER BY id LIMIT 1;

    -- ============================================================
    -- 1) exchange_rates: USD/COP vigente
    -- ============================================================
    IF v_currency_cop_id IS NOT NULL AND v_currency_usd_id IS NOT NULL THEN
        INSERT INTO exchange_rates (company_id, currency_id, currency_iso, value, exchange_type,
                                     start_date, end_date, status, created_at, updated_at)
        SELECT p_company_id, v_currency_usd_id, v_currency_cop_id, 4250.00 + p_suffix, 'OFICIAL',
               v_today - INTERVAL '30 days', v_today + INTERVAL '335 days', 'ACTIVE', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM exchange_rates
                           WHERE company_id=p_company_id AND currency_id=v_currency_usd_id
                             AND exchange_type='OFICIAL' AND deleted_at IS NULL);
    END IF;

    -- ============================================================
    -- 2) ruler_tax: IVA 19% + RTE FTE 2.5% + RETE ICA
    -- ============================================================
    IF v_acct_iva_id IS NOT NULL THEN
        INSERT INTO ruler_tax (company_id, accounting_account_id, name, percentage, type_ruler_tax,
                                start_date, end_date, status, description, scope, created_at, updated_at)
        SELECT p_company_id, v_acct_iva_id, 'IVA 19%', 19.0, 'TAX',
               v_today - INTERVAL '60 days', v_today + INTERVAL '730 days', 'ACTIVE',
               'IVA general 19%', 'GENERAL', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM ruler_tax WHERE company_id=p_company_id AND name='IVA 19%' AND deleted_at IS NULL);

        INSERT INTO ruler_tax (company_id, accounting_account_id, name, percentage, type_ruler_tax,
                                start_date, end_date, status, description, scope,
                                min_amount_uvt, uvt_value_year, created_at, updated_at)
        SELECT p_company_id, v_acct_iva_id, 'RTE FTE 2.5% Servicios', 2.5, 'WITHHOLDING',
               v_today - INTERVAL '60 days', v_today + INTERVAL '730 days', 'ACTIVE',
               'Retencion en fuente servicios generales', 'SERVICIOS',
               4, 47065, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM ruler_tax WHERE company_id=p_company_id AND name='RTE FTE 2.5% Servicios' AND deleted_at IS NULL);

        INSERT INTO ruler_tax (company_id, accounting_account_id, name, percentage, type_ruler_tax,
                                start_date, end_date, status, description, scope, created_at, updated_at)
        SELECT p_company_id, v_acct_iva_id, 'RETE ICA Bogota 0.966%', 0.966, 'WITHHOLDING',
               v_today - INTERVAL '60 days', v_today + INTERVAL '730 days', 'ACTIVE',
               'Retencion ICA actividad comercial Bogota', 'COMERCIAL', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM ruler_tax WHERE company_id=p_company_id AND name='RETE ICA Bogota 0.966%' AND deleted_at IS NULL);
    END IF;

    -- ============================================================
    -- 3) risk_segmentation: 1 por cliente principal
    -- ============================================================
    IF v_cliente1_id IS NOT NULL THEN
        INSERT INTO risk_segmentation (company_id, client_id, auto_segment, final_segment, segmentation_source,
                                        calculation_date, justification, created_at, updated_at)
        SELECT p_company_id, v_cliente1_id, 'LOW', 'LOW', 'AUTOMATIC',
               NOW(), 'Cliente con buen historial de pago', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM risk_segmentation
                           WHERE company_id=p_company_id AND client_id=v_cliente1_id AND deleted_at IS NULL);
    END IF;
    IF v_cliente2_id IS NOT NULL THEN
        INSERT INTO risk_segmentation (company_id, client_id, auto_segment, final_segment, segmentation_source,
                                        calculation_date, justification, created_at, updated_at)
        SELECT p_company_id, v_cliente2_id, 'MEDIUM', 'MEDIUM', 'AUTOMATIC',
               NOW(), 'Cliente con pagos ocasionalmente atrasados', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM risk_segmentation
                           WHERE company_id=p_company_id AND client_id=v_cliente2_id AND deleted_at IS NULL);
    END IF;

    -- ============================================================
    -- 4) commercial_data: por cliente
    -- ============================================================
    IF v_cliente1_id IS NOT NULL AND v_payment_term_id IS NOT NULL THEN
        INSERT INTO commercial_data (company_id, client_id, payment_term_id, currency_id,
                                      limit_credit, risk_level, validity_from, validity_to,
                                      created_at, updated_at)
        SELECT p_company_id, v_cliente1_id, v_payment_term_id, v_currency_cop_id,
               50000000, 'LOW', v_today - INTERVAL '30 days', v_today + INTERVAL '365 days',
               NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM commercial_data
                           WHERE company_id=p_company_id AND client_id=v_cliente1_id AND deleted_at IS NULL);
    END IF;
    IF v_cliente2_id IS NOT NULL AND v_payment_term_id IS NOT NULL THEN
        INSERT INTO commercial_data (company_id, client_id, payment_term_id, currency_id,
                                      limit_credit, risk_level, validity_from, validity_to,
                                      created_at, updated_at)
        SELECT p_company_id, v_cliente2_id, v_payment_term_id, v_currency_cop_id,
               30000000, 'MEDIUM', v_today - INTERVAL '30 days', v_today + INTERVAL '365 days',
               NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM commercial_data
                           WHERE company_id=p_company_id AND client_id=v_cliente2_id AND deleted_at IS NULL);
    END IF;

    -- ============================================================
    -- 5) bnk_cash_flow_projections
    -- ============================================================
    INSERT INTO bnk_cash_flow_projections (company_id, name, projection_type, periodicity, currency,
                                            start_date, end_date, initial_balance, final_balance, net_flow,
                                            status, description, created_at, updated_at)
    SELECT p_company_id, 'Proyeccion Flujo Q' || p_suffix || ' ' || v_year, 'NETA', 'TRIMESTRAL', 'COP',
           v_today, v_today + INTERVAL '90 days', 25000000, 35000000, 10000000,
           'BORRADOR', 'Proyeccion trimestral operativa QA', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM bnk_cash_flow_projections
                       WHERE company_id=p_company_id AND name='Proyeccion Flujo Q' || p_suffix || ' ' || v_year
                         AND deleted_at IS NULL);

    -- ============================================================
    -- 6) cash_audits
    -- ============================================================
    IF v_cash_id IS NOT NULL THEN
        INSERT INTO cash_audits (company_id, cash_id, audit_date, system_balance, physical_balance, difference,
                                  status, notes, created_by, created_at, updated_at)
        SELECT p_company_id, v_cash_id, v_today - INTERVAL '7 days',
               1000000, 999000, -1000, 'ABIERTO',
               'Arqueo semanal QA - faltante menor', v_admin_id, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM cash_audits
                           WHERE company_id=p_company_id AND cash_id=v_cash_id
                             AND audit_date=v_today - INTERVAL '7 days' AND deleted_at IS NULL);
    END IF;

    -- ============================================================
    -- 7) ap_advances
    -- ============================================================
    IF v_proveedor1_id IS NOT NULL AND v_bank_acct_id IS NOT NULL THEN
        INSERT INTO ap_advances (company_id, third_party_id, advance_date, amount, applied_amount,
                                  bank_account_id, status, notes, created_by, source,
                                  created_at, updated_at)
        SELECT p_company_id, v_proveedor1_id, v_today - INTERVAL '15 days', 1500000, 0,
               v_bank_acct_id, 'PENDING', 'Anticipo proveedor QA insumos', v_admin_id, 'MANUAL',
               NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM ap_advances
                           WHERE company_id=p_company_id AND third_party_id=v_proveedor1_id
                             AND advance_date=v_today - INTERVAL '15 days' AND deleted_at IS NULL);
    END IF;

    -- ============================================================
    -- 8) ap_credit_debit_notes (sobre la 1a factura compra)
    -- ============================================================
    IF v_fc_id IS NOT NULL THEN
        INSERT INTO ap_credit_debit_notes (company_id, invoice_id, note_type, note_number, amount,
                                            reason, source, created_by, created_at, updated_at)
        SELECT p_company_id, v_fc_id, 'CREDIT',
               'NC-FC-' || v_year || LPAD((p_suffix*100 + 1)::TEXT, 6, '0'), 50000,
               'Devolucion parcial mercancia QA', 'MANUAL', v_admin_id, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM ap_credit_debit_notes
                           WHERE company_id=p_company_id AND invoice_id=v_fc_id
                             AND note_type='CREDIT' AND deleted_at IS NULL);
    END IF;

    -- ============================================================
    -- 9) purchase_orders + lines + goods_receipts
    -- ============================================================
    IF v_proveedor2_id IS NOT NULL THEN
        INSERT INTO purchase_orders (company_id, third_party_id, order_number, order_date,
                                      delivery_date, total_amount, status, notes, created_by,
                                      created_at, updated_at)
        SELECT p_company_id, v_proveedor2_id,
               'OC-QA' || p_suffix || '-' || v_year || LPAD('1', 6, '0'),
               v_today - INTERVAL '20 days', v_today - INTERVAL '5 days',
               2500000, 'APPROVED',
               'Orden compra equipo computacion QA', v_admin_id, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM purchase_orders
                           WHERE company_id=p_company_id
                             AND order_number='OC-QA' || p_suffix || '-' || v_year || LPAD('1', 6, '0')
                             AND deleted_at IS NULL);

        SELECT id INTO v_po_id FROM purchase_orders
         WHERE company_id=p_company_id
           AND order_number='OC-QA' || p_suffix || '-' || v_year || LPAD('1', 6, '0')
           AND deleted_at IS NULL LIMIT 1;

        IF v_po_id IS NOT NULL THEN
            -- Linea unica de la OC
            INSERT INTO purchase_order_lines (company_id, purchase_order_id, description,
                                                quantity, unit_price, total_line, created_at)
            SELECT p_company_id, v_po_id, 'Equipo de computo Dell OptiPlex QA',
                   1, 2500000, 2500000, NOW()
            WHERE NOT EXISTS (SELECT 1 FROM purchase_order_lines
                               WHERE company_id=p_company_id AND purchase_order_id=v_po_id
                                 AND deleted_at IS NULL);

            -- Recepcion asociada
            INSERT INTO goods_receipts (company_id, purchase_order_id, receipt_number,
                                         receipt_date, status, notes, created_by,
                                         created_at, updated_at)
            SELECT p_company_id, v_po_id,
                   'REC-QA' || p_suffix || '-' || v_year || LPAD('1', 6, '0'),
                   v_today - INTERVAL '5 days', 'COMPLETED',
                   'Recepcion completa equipo QA', v_admin_id, NOW(), NOW()
            WHERE NOT EXISTS (SELECT 1 FROM goods_receipts
                               WHERE company_id=p_company_id AND purchase_order_id=v_po_id
                                 AND deleted_at IS NULL);
        END IF;
    END IF;

    -- ============================================================
    -- 10) ar_advances
    -- ============================================================
    IF v_cliente1_id IS NOT NULL AND v_bank_acct_id IS NOT NULL THEN
        INSERT INTO ar_advances (company_id, third_party_id, advance_date, amount, applied_amount,
                                  bank_account_id, status, notes, created_by, source,
                                  created_at, updated_at)
        SELECT p_company_id, v_cliente1_id, v_today - INTERVAL '10 days', 800000, 0,
               v_bank_acct_id, 'PENDING', 'Anticipo cliente QA pedido futuro', v_admin_id, 'MANUAL',
               NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM ar_advances
                           WHERE company_id=p_company_id AND third_party_id=v_cliente1_id
                             AND advance_date=v_today - INTERVAL '10 days' AND deleted_at IS NULL);
    END IF;

    -- ============================================================
    -- 11) ar_credit_debit_notes (sobre la 1a factura venta)
    -- ============================================================
    IF v_fv_id IS NOT NULL THEN
        INSERT INTO ar_credit_debit_notes (company_id, invoice_id, note_type, note_number, amount,
                                            reason, source, created_by, created_at, updated_at)
        SELECT p_company_id, v_fv_id, 'CREDIT',
               'NC-FV-' || v_year || LPAD((p_suffix*100 + 1)::TEXT, 6, '0'), 100000,
               'Descuento postventa cliente QA', 'MANUAL', v_admin_id, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM ar_credit_debit_notes
                           WHERE company_id=p_company_id AND invoice_id=v_fv_id
                             AND note_type='CREDIT' AND deleted_at IS NULL);
    END IF;

    RAISE NOTICE '_qa_ext_seed_company: empresa=% poblada OK', p_company_id;
END $$;

-- ================================================================
-- LOOP sobre las 6 empresas QA (id 4..9 segun seed previo)
-- ================================================================
DO $$
    DECLARE
        r RECORD;
        v_suffix INT := 1;
    BEGIN
        FOR r IN
            SELECT id, business_name FROM companies
             WHERE business_name LIKE 'EMPRESA QA % SAS'
               AND deleted_at IS NULL
             ORDER BY id
        LOOP
            -- Extraer suffix del nombre "EMPRESA QA N SAS"
            BEGIN
                v_suffix := SUBSTRING(r.business_name FROM 'EMPRESA QA (\d+) SAS')::INT;
            EXCEPTION WHEN OTHERS THEN
                v_suffix := v_suffix + 1;
            END;
            PERFORM _qa_ext_seed_company(r.id, v_suffix);
        END LOOP;
    END $$;

-- Cleanup: NO dropear la funcion para permitir re-ejecucion manual.

SELECT 'V9-ZZD aplicado: 12 submodulos poblados en 6 empresas QA' AS status;
