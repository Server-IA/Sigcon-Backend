-- V9-ZZ: Comprehensive seed for empresa "test" (company_id=3)
-- Idempotente: cada bloque usa WHERE NOT EXISTS y DO BEGIN con manejo defensivo.
-- No falla el batch completo si falta una FK opcional: skip con RAISE NOTICE.

-- =============================================================================
-- BLOQUE 1: Chequera + 20 cheques disponibles + 3 cheques emitidos/cobrados/anulados
-- =============================================================================
DO $$
DECLARE
    v_company_id  CONSTANT BIGINT := 3;
    v_bank_acct   BIGINT;
    v_checkbook   BIGINT;
    v_third       BIGINT;
BEGIN
    SELECT id INTO v_bank_acct FROM bank_accounts
     WHERE company_id=v_company_id AND deleted_at IS NULL
     ORDER BY id LIMIT 1;

    IF v_bank_acct IS NULL THEN
        RAISE NOTICE 'V9-ZZ: No hay bank_account en company 3, skip chequera/cheques';
        RETURN;
    END IF;

    -- 1a) Checkbook (1 chequera rango 1000-1019)
    IF NOT EXISTS (
        SELECT 1 FROM checkbooks
         WHERE company_id=v_company_id AND bank_account_id=v_bank_acct
           AND checkbook_number='CHQ-DEMO-001' AND deleted_at IS NULL
    ) THEN
        INSERT INTO checkbooks (
            company_id, bank_account_id, checkbook_number,
            check_start_number, check_end_number, total_checks, used_checks, available_checks,
            status, activation_date, received_date, issuing_bank, observations,
            created_at, updated_at
        ) VALUES (
            v_company_id, v_bank_acct, 'CHQ-DEMO-001',
            1000, 1019, 20, 3, 17,
            'ACTIVA', CURRENT_DATE - 30, CURRENT_DATE - 30, 'BANCOLOMBIA', 'Chequera demo QA',
            NOW(), NOW()
        );
        RAISE NOTICE 'V9-ZZ: checkbook CHQ-DEMO-001 creada';
    END IF;

    SELECT id INTO v_checkbook FROM checkbooks
     WHERE company_id=v_company_id AND checkbook_number='CHQ-DEMO-001' AND deleted_at IS NULL;

    SELECT id INTO v_third FROM third_parties
     WHERE company_id=v_company_id AND deleted_at IS NULL
     ORDER BY id LIMIT 1;

    -- 1b) 3 cheques en estados distintos: EMITIDO, COBRADO, ANULADO
    IF v_checkbook IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM checks WHERE checkbooks_id=v_checkbook AND number_check=1000
    ) THEN
        INSERT INTO checks (
            company_id, checkbooks_id, number_check, beneficiary, concept,
            value, issue_date, status_check, type_check, block_payment,
            created_at, updated_at
        ) VALUES (
            v_company_id, v_checkbook, 1000,
            'PROVEEDOR QA 1 LTDA', 'Pago factura proveedor - demo emitido',
            500000.00, CURRENT_DATE - 10, 'EMITIDO', 'FISICO', false,
            NOW(), NOW()
        );
    END IF;

    IF v_checkbook IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM checks WHERE checkbooks_id=v_checkbook AND number_check=1001
    ) THEN
        INSERT INTO checks (
            company_id, checkbooks_id, number_check, beneficiary, concept,
            value, issue_date, status_check, type_check, block_payment,
            collection_date, collection_reference,
            created_at, updated_at
        ) VALUES (
            v_company_id, v_checkbook, 1001,
            'PROVEEDOR QA 2 LTDA', 'Pago servicios - demo cobrado',
            750000.00, CURRENT_DATE - 20, 'COBRADO', 'FISICO', false,
            CURRENT_DATE - 15, 'REF-COBRO-001',
            NOW(), NOW()
        );
    END IF;

    IF v_checkbook IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM checks WHERE checkbooks_id=v_checkbook AND number_check=1002
    ) THEN
        INSERT INTO checks (
            company_id, checkbooks_id, number_check, beneficiary, concept,
            value, issue_date, status_check, type_check, block_payment,
            void_reason, voided_at,
            created_at, updated_at
        ) VALUES (
            v_company_id, v_checkbook, 1002,
            'PROVEEDOR QA 3 LTDA', 'Pago anulado - error de monto',
            300000.00, CURRENT_DATE - 5, 'ANULADO', 'FISICO', true,
            'Error de monto, emitido por equivocacion', NOW() - INTERVAL '3 days',
            NOW(), NOW()
        );
    END IF;

    RAISE NOTICE 'V9-ZZ: cheques demo (EMITIDO/COBRADO/ANULADO) asegurados';
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'V9-ZZ [Bloque 1 cheques] FALLO: %', SQLERRM;
END $$;

-- =============================================================================
-- BLOQUE 2: 2 movimientos financieros (1 ingreso + 1 egreso) en la cuenta bancaria
-- =============================================================================
DO $$
DECLARE
    v_company_id  CONSTANT BIGINT := 3;
    v_bank_acct   BIGINT;
    v_acc_ing     BIGINT; -- ingresos 4135
    v_acc_gst     BIGINT; -- gastos 5135
BEGIN
    SELECT id INTO v_bank_acct FROM bank_accounts
     WHERE company_id=v_company_id AND deleted_at IS NULL ORDER BY id LIMIT 1;

    SELECT aa.id INTO v_acc_ing FROM accounting_accounts aa
      JOIN cfg_chart_of_accounts coa ON coa.id=aa.puc_id
     WHERE aa.company_id=v_company_id AND aa.deleted_at IS NULL AND coa.account_code='4135' LIMIT 1;
    SELECT aa.id INTO v_acc_gst FROM accounting_accounts aa
      JOIN cfg_chart_of_accounts coa ON coa.id=aa.puc_id
     WHERE aa.company_id=v_company_id AND aa.deleted_at IS NULL AND coa.account_code='5135' LIMIT 1;

    IF v_bank_acct IS NULL THEN
        RAISE NOTICE 'V9-ZZ [fin_mov]: sin bank_account, skip'; RETURN;
    END IF;

    -- Ingreso
    IF NOT EXISTS (
        SELECT 1 FROM financial_movements
         WHERE company_id=v_company_id AND external_reference='FM-DEMO-ING-001'
    ) THEN
        INSERT INTO financial_movements (
            company_id, bank_account_id, amount, description,
            movement_date, source_type, flow_activity, external_reference,
            created_at, updated_at
        ) VALUES (
            v_company_id, v_bank_acct, 2500000.00, 'Ingreso demo - depósito en banco',
            CURRENT_DATE - 7, 'MANUAL', 'OPERATIVA', 'FM-DEMO-ING-001',
            NOW(), NOW()
        );
    END IF;

    -- Egreso
    IF NOT EXISTS (
        SELECT 1 FROM financial_movements
         WHERE company_id=v_company_id AND external_reference='FM-DEMO-EGR-001'
    ) THEN
        INSERT INTO financial_movements (
            company_id, bank_account_id, amount, description,
            movement_date, source_type, flow_activity, external_reference,
            created_at, updated_at
        ) VALUES (
            v_company_id, v_bank_acct, 800000.00, 'Egreso demo - pago servicios publicos',
            CURRENT_DATE - 3, 'MANUAL', 'OPERATIVA', 'FM-DEMO-EGR-001',
            NOW(), NOW()
        );
    END IF;

    RAISE NOTICE 'V9-ZZ: financial_movements demo asegurados';
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'V9-ZZ [Bloque 2 fin_mov] FALLO: %', SQLERRM;
END $$;

-- =============================================================================
-- BLOQUE 3: Resolucion DIAN activa
-- =============================================================================
DO $$
DECLARE v_company_id CONSTANT BIGINT := 3;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM dian_resolutions
         WHERE company_id=v_company_id AND resolution_number='RESOLUCION-000001'
           AND deleted_at IS NULL
    ) THEN
        INSERT INTO dian_resolutions (
            company_id, resolution_number, prefix, start_date, end_date,
            start_number, end_number, current_number, status, notes,
            created_at, updated_at
        ) VALUES (
            v_company_id, 'RESOLUCION-000001', 'FV',
            CURRENT_DATE, CURRENT_DATE + INTERVAL '2 years',
            1, 1000, 2, 'ACTIVE', 'Resolucion demo QA para empresa test',
            NOW(), NOW()
        );
        RAISE NOTICE 'V9-ZZ: dian_resolution demo creada';
    END IF;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'V9-ZZ [Bloque 3 DIAN] FALLO: %', SQLERRM;
END $$;

-- =============================================================================
-- BLOQUE 4: 2 Facturas de venta (FV-2026000001 ISSUED, FV-2026000002 PAID) + lineas
-- =============================================================================
DO $$
DECLARE
    v_company_id CONSTANT BIGINT := 3;
    v_client     BIGINT;
    v_pform      BIGINT;
    v_fv_1       BIGINT;
    v_fv_2       BIGINT;
BEGIN
    SELECT id INTO v_client FROM third_parties
     WHERE company_id=v_company_id AND deleted_at IS NULL
       AND business_name ILIKE '%CLIENTE%'
     ORDER BY id LIMIT 1;
    IF v_client IS NULL THEN
        SELECT id INTO v_client FROM third_parties
         WHERE company_id=v_company_id AND deleted_at IS NULL
         ORDER BY id LIMIT 1;
    END IF;

    SELECT id INTO v_pform FROM payment_forms WHERE deleted_at IS NULL ORDER BY id LIMIT 1;

    IF v_client IS NULL THEN
        RAISE NOTICE 'V9-ZZ [FV]: sin cliente, skip'; RETURN;
    END IF;

    -- FV 1 ISSUED (2 lineas, IVA 19%)
    IF NOT EXISTS (
        SELECT 1 FROM sales_invoices
         WHERE company_id=v_company_id AND invoice_number='FV-2026000001' AND deleted_at IS NULL
    ) THEN
        INSERT INTO sales_invoices (
            company_id, invoice_number, third_party_id, invoice_date, due_date,
            status, subtotal, total_tax, total_withholding, total_amount, balance_due,
            exchange_rate, payment_form_id, xml_sent, notes, source,
            created_at, updated_at
        ) VALUES (
            v_company_id, 'FV-2026000001', v_client, CURRENT_DATE - 5, CURRENT_DATE + 25,
            'ISSUED', 1000000.00, 190000.00, 0.00, 1190000.00, 1190000.00,
            1.0, v_pform, false, 'Factura venta demo ISSUED', 'MANUAL',
            NOW(), NOW()
        );
    END IF;
    SELECT id INTO v_fv_1 FROM sales_invoices
     WHERE company_id=v_company_id AND invoice_number='FV-2026000001' AND deleted_at IS NULL;

    IF v_fv_1 IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM sales_invoice_lines WHERE invoice_id=v_fv_1 AND deleted_at IS NULL
    ) THEN
        INSERT INTO sales_invoice_lines (
            company_id, invoice_id, description, quantity, unit_price, discount,
            subtotal, tax_amount, withholding_amount, total, created_at, updated_at
        ) VALUES
        (v_company_id, v_fv_1, 'Consultoria tecnica - horas', 10, 60000.00, 0.00,
         600000.00, 114000.00, 0.00, 714000.00, NOW()),
        (v_company_id, v_fv_1, 'Licencia software anual', 1, 400000.00, 0.00,
         400000.00, 76000.00, 0.00, 476000.00, NOW());
    END IF;

    -- FV 2 PAID (1 linea, IVA 19%)
    IF NOT EXISTS (
        SELECT 1 FROM sales_invoices
         WHERE company_id=v_company_id AND invoice_number='FV-2026000002' AND deleted_at IS NULL
    ) THEN
        INSERT INTO sales_invoices (
            company_id, invoice_number, third_party_id, invoice_date, due_date,
            status, subtotal, total_tax, total_withholding, total_amount, balance_due,
            exchange_rate, payment_form_id, xml_sent, notes, source,
            created_at, updated_at
        ) VALUES (
            v_company_id, 'FV-2026000002', v_client, CURRENT_DATE - 15, CURRENT_DATE - 5,
            'PAID', 500000.00, 95000.00, 0.00, 595000.00, 0.00,
            1.0, v_pform, false, 'Factura venta demo PAID', 'MANUAL',
            NOW(), NOW()
        );
    END IF;
    SELECT id INTO v_fv_2 FROM sales_invoices
     WHERE company_id=v_company_id AND invoice_number='FV-2026000002' AND deleted_at IS NULL;

    IF v_fv_2 IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM sales_invoice_lines WHERE invoice_id=v_fv_2 AND deleted_at IS NULL
    ) THEN
        INSERT INTO sales_invoice_lines (
            company_id, invoice_id, description, quantity, unit_price, discount,
            subtotal, tax_amount, withholding_amount, total, created_at, updated_at
        ) VALUES
        (v_company_id, v_fv_2, 'Servicio mantenimiento', 1, 500000.00, 0.00,
         500000.00, 95000.00, 0.00, 595000.00, NOW());
    END IF;

    RAISE NOTICE 'V9-ZZ: sales_invoices + lineas demo asegurados';
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'V9-ZZ [Bloque 4 FV] FALLO: %', SQLERRM;
END $$;

-- =============================================================================
-- BLOQUE 5: 1 cobro AR (ar_payment) vinculado a FV-2026000002
-- =============================================================================
DO $$
DECLARE
    v_company_id CONSTANT BIGINT := 3;
    v_fv_2       BIGINT;
    v_bank_acct  BIGINT;
BEGIN
    SELECT id INTO v_fv_2 FROM sales_invoices
     WHERE company_id=v_company_id AND invoice_number='FV-2026000002' AND deleted_at IS NULL;
    SELECT id INTO v_bank_acct FROM bank_accounts
     WHERE company_id=v_company_id AND deleted_at IS NULL ORDER BY id LIMIT 1;

    IF v_fv_2 IS NULL OR v_bank_acct IS NULL THEN
        RAISE NOTICE 'V9-ZZ [AR payment]: FV-2 o cuenta bancaria ausentes, skip'; RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM ar_payments
         WHERE company_id=v_company_id AND invoice_id=v_fv_2 AND payment_reference='AR-PAY-DEMO-001'
           AND deleted_at IS NULL
    ) THEN
        INSERT INTO ar_payments (
            company_id, invoice_id, amount, payment_date, payment_method,
            payment_reference, notes, status, bank_account_id, source,
            created_at, updated_at
        ) VALUES (
            v_company_id, v_fv_2, 595000.00, CURRENT_DATE - 5, 'TRANSFER',
            'AR-PAY-DEMO-001', 'Cobro demo - pago completo FV-2026000002', 'CONFIRMED',
            v_bank_acct, 'MANUAL', NOW(), NOW()
        );
        RAISE NOTICE 'V9-ZZ: ar_payment demo creado';
    END IF;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'V9-ZZ [Bloque 5 AR pay] FALLO: %', SQLERRM;
END $$;

-- =============================================================================
-- BLOQUE 6: 1 orden de compra APPROVED + 1 linea
-- =============================================================================
DO $$
DECLARE
    v_company_id CONSTANT BIGINT := 3;
    v_prov       BIGINT;
    v_po         BIGINT;
    v_admin      BIGINT;
BEGIN
    SELECT id INTO v_prov FROM third_parties
     WHERE company_id=v_company_id AND deleted_at IS NULL
       AND business_name ILIKE '%PROVEEDOR%'
     ORDER BY id LIMIT 1;
    IF v_prov IS NULL THEN
        SELECT id INTO v_prov FROM third_parties
         WHERE company_id=v_company_id AND deleted_at IS NULL ORDER BY id DESC LIMIT 1;
    END IF;
    SELECT id INTO v_admin FROM users WHERE company_id=v_company_id LIMIT 1;

    IF v_prov IS NULL THEN
        RAISE NOTICE 'V9-ZZ [PO]: sin proveedor, skip'; RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM purchase_orders
         WHERE company_id=v_company_id AND order_number='OC-DEMO-001' AND deleted_at IS NULL
    ) THEN
        INSERT INTO purchase_orders (
            company_id, third_party_id, order_number, order_date, status,
            total_amount, created_by, approved_by, approved_at,
            delivery_date, notes, created_at, updated_at
        ) VALUES (
            v_company_id, v_prov, 'OC-DEMO-001', CURRENT_DATE - 20, 'APPROVED',
            1190000.00, v_admin, v_admin, NOW() - INTERVAL '15 days',
            CURRENT_DATE - 10, 'Orden de compra demo aprobada',
            NOW(), NOW()
        );
    END IF;
    SELECT id INTO v_po FROM purchase_orders
     WHERE company_id=v_company_id AND order_number='OC-DEMO-001' AND deleted_at IS NULL;

    IF v_po IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM purchase_order_lines WHERE purchase_order_id=v_po AND deleted_at IS NULL
    ) THEN
        INSERT INTO purchase_order_lines (
            company_id, purchase_order_id, description,
            quantity, unit_price, total_line,
            created_at
        ) VALUES (
            v_company_id, v_po, 'Insumos de oficina demo',
            10, 119000.00, 1190000.00,
            NOW()
        );
    END IF;

    RAISE NOTICE 'V9-ZZ: purchase_order demo asegurada';
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'V9-ZZ [Bloque 6 PO] FALLO: %', SQLERRM;
END $$;

-- =============================================================================
-- BLOQUE 7: 1 recepcion vinculada a la OC
-- =============================================================================
DO $$
DECLARE
    v_company_id CONSTANT BIGINT := 3;
    v_po         BIGINT;
    v_pol        BIGINT;
    v_gr         BIGINT;
BEGIN
    SELECT id INTO v_po FROM purchase_orders
     WHERE company_id=v_company_id AND order_number='OC-DEMO-001' AND deleted_at IS NULL;
    IF v_po IS NULL THEN
        RAISE NOTICE 'V9-ZZ [GR]: OC-DEMO-001 ausente, skip'; RETURN;
    END IF;

    SELECT id INTO v_pol FROM purchase_order_lines WHERE purchase_order_id=v_po AND deleted_at IS NULL LIMIT 1;

    IF NOT EXISTS (
        SELECT 1 FROM goods_receipts
         WHERE company_id=v_company_id AND receipt_number='RM-DEMO-001' AND deleted_at IS NULL
    ) THEN
        INSERT INTO goods_receipts (
            company_id, purchase_order_id, receipt_number, receipt_date, status,
            notes, created_at, updated_at
        ) VALUES (
            v_company_id, v_po, 'RM-DEMO-001', CURRENT_DATE - 10, 'RECEIVED',
            'Recepcion demo completa', NOW(), NOW()
        );
    END IF;
    SELECT id INTO v_gr FROM goods_receipts
     WHERE company_id=v_company_id AND receipt_number='RM-DEMO-001' AND deleted_at IS NULL;

    IF v_gr IS NOT NULL AND v_pol IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM goods_receipt_lines WHERE goods_receipt_id=v_gr AND deleted_at IS NULL
    ) THEN
        INSERT INTO goods_receipt_lines (
            company_id, goods_receipt_id, purchase_order_line_id,
            quantity_received, created_at
        ) VALUES (
            v_company_id, v_gr, v_pol, 10, NOW()
        );
    END IF;

    RAISE NOTICE 'V9-ZZ: goods_receipt demo asegurada';
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'V9-ZZ [Bloque 7 GR] FALLO: %', SQLERRM;
END $$;

-- =============================================================================
-- BLOQUE 8: 2 Facturas de compra adicionales (PENDING con IVA, PAID liquidada)
-- =============================================================================
DO $$
DECLARE
    v_company_id CONSTANT BIGINT := 3;
    v_prov       BIGINT;
    v_fc_type    BIGINT;
    v_state_pend BIGINT;
    v_state_paid BIGINT;
    v_pform      BIGINT;
    v_user       BIGINT;
    v_acc_gst    BIGINT;
    v_inv_1      BIGINT;
    v_inv_2      BIGINT;
BEGIN
    SELECT id INTO v_prov FROM third_parties
     WHERE company_id=v_company_id AND deleted_at IS NULL
       AND business_name ILIKE '%PROVEEDOR%'
     ORDER BY id LIMIT 1;
    IF v_prov IS NULL THEN
        SELECT id INTO v_prov FROM third_parties WHERE company_id=v_company_id AND deleted_at IS NULL ORDER BY id DESC LIMIT 1;
    END IF;

    SELECT id INTO v_fc_type FROM types_invoices WHERE code='FC' AND deleted_at IS NULL;
    SELECT id INTO v_state_pend FROM invoice_states WHERE code='PEND' AND deleted_at IS NULL ORDER BY id LIMIT 1;
    SELECT id INTO v_state_paid FROM invoice_states WHERE code='FIN'  AND deleted_at IS NULL ORDER BY id LIMIT 1;
    SELECT id INTO v_pform FROM payment_forms WHERE deleted_at IS NULL ORDER BY id LIMIT 1;
    SELECT id INTO v_user  FROM users WHERE company_id=v_company_id LIMIT 1;
    SELECT aa.id INTO v_acc_gst FROM accounting_accounts aa
       JOIN cfg_chart_of_accounts coa ON coa.id=aa.puc_id
     WHERE aa.company_id=v_company_id AND aa.deleted_at IS NULL AND coa.account_code='5135' LIMIT 1;

    IF v_prov IS NULL OR v_fc_type IS NULL OR v_state_pend IS NULL OR v_pform IS NULL OR v_user IS NULL THEN
        RAISE NOTICE 'V9-ZZ [FC]: falta FK critica (prov=%, fc=%, st_pend=%, pf=%, user=%) skip',
            v_prov, v_fc_type, v_state_pend, v_pform, v_user;
        RETURN;
    END IF;

    -- FC PENDING
    IF NOT EXISTS (
        SELECT 1 FROM invoices
         WHERE company_id=v_company_id AND supplier_invoice_number='FC-DEMO-PEND-001' AND deleted_at IS NULL
    ) THEN
        INSERT INTO invoices (
            company_id, third_party_id, type_invoice_id, invoice_state_id, invoice_status,
            supplier_invoice_number, resolution, resolution_invoice,
            invoice_date, invoice_due_day,
            total_discount, total_tax, total_payment, total_amount, balance_due,
            payment_forms_id, user_id, notes, source,
            created_at, updated_at
        ) VALUES (
            v_company_id, v_prov, v_fc_type, v_state_pend, 'PENDING',
            'FC-DEMO-PEND-001', 'RES-DEMO-PEND-001', 'RES-DEMO-PEND-001',
            CURRENT_DATE - 8, 30,
            0.00, 190000.00, 1190000.00, 1190000.00, 1190000.00,
            v_pform, v_user, 'Factura compra demo PENDING', 'MANUAL',
            NOW(), NOW()
        );
    END IF;
    SELECT id INTO v_inv_1 FROM invoices
     WHERE company_id=v_company_id AND supplier_invoice_number='FC-DEMO-PEND-001' AND deleted_at IS NULL;

    IF v_inv_1 IS NOT NULL AND v_acc_gst IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM lines_invoice WHERE invoice_id=v_inv_1 AND deleted_at IS NULL
    ) THEN
        INSERT INTO lines_invoice (
            company_id, invoice_id, description, quantity, price, discount, tax, total,
            accounting_account_id, created_at, updated_at
        ) VALUES (
            v_company_id, v_inv_1, 'Servicios demo PENDING', 1, 1000000.00, 0.00, 190000.00, 1190000.00,
            v_acc_gst, NOW(), NOW()
        );
    END IF;

    -- FC PAID/SETTLED
    IF v_state_paid IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM invoices
         WHERE company_id=v_company_id AND supplier_invoice_number='FC-DEMO-PAID-001' AND deleted_at IS NULL
    ) THEN
        INSERT INTO invoices (
            company_id, third_party_id, type_invoice_id, invoice_state_id, invoice_status,
            supplier_invoice_number, resolution, resolution_invoice,
            invoice_date, invoice_due_day,
            total_discount, total_tax, total_payment, total_amount, balance_due,
            payment_forms_id, user_id, notes, source,
            created_at, updated_at
        ) VALUES (
            v_company_id, v_prov, v_fc_type, v_state_paid, 'PAID',
            'FC-DEMO-PAID-001', 'RES-DEMO-PAID-001', 'RES-DEMO-PAID-001',
            CURRENT_DATE - 18, 30,
            0.00, 57000.00, 357000.00, 357000.00, 0.00,
            v_pform, v_user, 'Factura compra demo PAID', 'MANUAL',
            NOW(), NOW()
        );
    END IF;
    SELECT id INTO v_inv_2 FROM invoices
     WHERE company_id=v_company_id AND supplier_invoice_number='FC-DEMO-PAID-001' AND deleted_at IS NULL;

    IF v_inv_2 IS NOT NULL AND v_acc_gst IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM lines_invoice WHERE invoice_id=v_inv_2 AND deleted_at IS NULL
    ) THEN
        INSERT INTO lines_invoice (
            company_id, invoice_id, description, quantity, price, discount, tax, total,
            accounting_account_id, created_at, updated_at
        ) VALUES (
            v_company_id, v_inv_2, 'Servicios demo PAID', 1, 300000.00, 0.00, 57000.00, 357000.00,
            v_acc_gst, NOW(), NOW()
        );
    END IF;

    RAISE NOTICE 'V9-ZZ: invoices AP adicionales demo aseguradas';
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'V9-ZZ [Bloque 8 FC adicionales] FALLO: %', SQLERRM;
END $$;

-- =============================================================================
-- BLOQUE 9: 1 pago AP vinculado a la factura AP PAID
-- =============================================================================
DO $$
DECLARE
    v_company_id CONSTANT BIGINT := 3;
    v_inv_paid   BIGINT;
    v_bank_acct  BIGINT;
BEGIN
    SELECT id INTO v_inv_paid FROM invoices
     WHERE company_id=v_company_id AND supplier_invoice_number='FC-DEMO-PAID-001' AND deleted_at IS NULL;
    SELECT id INTO v_bank_acct FROM bank_accounts
     WHERE company_id=v_company_id AND deleted_at IS NULL ORDER BY id LIMIT 1;

    IF v_inv_paid IS NULL OR v_bank_acct IS NULL THEN
        RAISE NOTICE 'V9-ZZ [AP pay]: factura PAID o bank ausente, skip'; RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM ap_payments
         WHERE company_id=v_company_id AND invoice_id=v_inv_paid AND payment_reference='AP-PAY-DEMO-001'
           AND deleted_at IS NULL
    ) THEN
        INSERT INTO ap_payments (
            company_id, invoice_id, amount, payment_date, payment_method,
            payment_reference, notes, status, bank_account_id, source,
            created_at, updated_at
        ) VALUES (
            v_company_id, v_inv_paid, 357000.00, CURRENT_DATE - 10, 'TRANSFER',
            'AP-PAY-DEMO-001', 'Pago demo - liquida FC-DEMO-PAID-001', 'CONFIRMED',
            v_bank_acct, 'MANUAL', NOW(), NOW()
        );
        RAISE NOTICE 'V9-ZZ: ap_payment demo creado';
    END IF;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'V9-ZZ [Bloque 9 AP pay] FALLO: %', SQLERRM;
END $$;

-- =============================================================================
-- BLOQUE 10: 2 recibos de nomina APPROVED del periodo actual + lineas
-- =============================================================================
DO $$
DECLARE
    v_company_id CONSTANT BIGINT := 3;
    v_year       INT := EXTRACT(YEAR FROM CURRENT_DATE)::INT;
    v_month      INT := EXTRACT(MONTH FROM CURRENT_DATE)::INT;
    v_emp1       BIGINT;
    v_emp2       BIGINT;
    v_r1         BIGINT;
    v_r2         BIGINT;
    v_c_salario  BIGINT;
    v_c_salud    BIGINT;
    v_c_pension  BIGINT;
    v_c_salud_e  BIGINT;
    v_c_pens_e   BIGINT;
BEGIN
    SELECT id INTO v_emp1 FROM employees WHERE company_id=v_company_id AND deleted_at IS NULL ORDER BY id LIMIT 1;
    SELECT id INTO v_emp2 FROM employees WHERE company_id=v_company_id AND deleted_at IS NULL ORDER BY id OFFSET 1 LIMIT 1;

    SELECT id INTO v_c_salario FROM payroll_concepts WHERE company_id=v_company_id AND code='SALARIO_BASE'    AND deleted_at IS NULL;
    SELECT id INTO v_c_salud   FROM payroll_concepts WHERE company_id=v_company_id AND code='SALUD_EMPLEADO'  AND deleted_at IS NULL;
    SELECT id INTO v_c_pension FROM payroll_concepts WHERE company_id=v_company_id AND code='PENSION_EMPLEADO' AND deleted_at IS NULL;
    SELECT id INTO v_c_salud_e FROM payroll_concepts WHERE company_id=v_company_id AND code='SALUD_EMPRESA'   AND deleted_at IS NULL;
    SELECT id INTO v_c_pens_e  FROM payroll_concepts WHERE company_id=v_company_id AND code='PENSION_EMPRESA' AND deleted_at IS NULL;

    IF v_emp1 IS NULL OR v_c_salario IS NULL THEN
        RAISE NOTICE 'V9-ZZ [NOM]: sin empleado o concepto base, skip'; RETURN;
    END IF;

    -- Recibo 1: empleado 1 - salario 3M
    IF NOT EXISTS (
        SELECT 1 FROM payroll_receipts
         WHERE company_id=v_company_id AND employee_id=v_emp1
           AND period_year=v_year AND period_month=v_month
           AND deleted_at IS NULL
    ) THEN
        INSERT INTO payroll_receipts (
            company_id, employee_id, period_year, period_month, period_type, days_worked,
            total_earnings, total_deductions, total_employer_contributions, net_pay,
            status, approved_by, approved_at, notes,
            period_start, period_end,
            created_at, updated_at
        ) VALUES (
            v_company_id, v_emp1, v_year, v_month, 'MONTHLY', 30,
            3000000.00, 240000.00, 540000.00, 2760000.00,
            'APPROVED', 'admin.tenant3', NOW() - INTERVAL '2 days', 'Recibo demo mensual emp1',
            DATE_TRUNC('month', CURRENT_DATE)::DATE,
            (DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')::DATE,
            NOW(), NOW()
        );
    END IF;
    SELECT id INTO v_r1 FROM payroll_receipts
     WHERE company_id=v_company_id AND employee_id=v_emp1 AND period_year=v_year AND period_month=v_month
       AND deleted_at IS NULL LIMIT 1;

    IF v_r1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM payroll_lines WHERE receipt_id=v_r1 AND deleted_at IS NULL) THEN
        INSERT INTO payroll_lines (company_id, receipt_id, concept_code, concept_name, line_type, line_order, amount, created_at) VALUES
        (v_company_id, v_r1, 'SALARIO_BASE',     'Salario base',             'EARNING',               1, 3000000.00, NOW()),
        (v_company_id, v_r1, 'SALUD_EMPLEADO',   'Aporte salud empleado',    'DEDUCTION',             2,  120000.00, NOW()),
        (v_company_id, v_r1, 'PENSION_EMPLEADO', 'Aporte pension empleado',  'DEDUCTION',             3,  120000.00, NOW()),
        (v_company_id, v_r1, 'SALUD_EMPRESA',    'Aporte salud empresa',     'EMPLOYER_CONTRIBUTION', 4,  255000.00, NOW()),
        (v_company_id, v_r1, 'PENSION_EMPRESA',  'Aporte pension empresa',   'EMPLOYER_CONTRIBUTION', 5,  285000.00, NOW());
    END IF;

    -- Recibo 2: empleado 2 - salario 2.5M
    IF v_emp2 IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM payroll_receipts
         WHERE company_id=v_company_id AND employee_id=v_emp2
           AND period_year=v_year AND period_month=v_month
           AND deleted_at IS NULL
    ) THEN
        INSERT INTO payroll_receipts (
            company_id, employee_id, period_year, period_month, period_type, days_worked,
            total_earnings, total_deductions, total_employer_contributions, net_pay,
            status, approved_by, approved_at, notes,
            period_start, period_end,
            created_at, updated_at
        ) VALUES (
            v_company_id, v_emp2, v_year, v_month, 'MONTHLY', 30,
            2500000.00, 200000.00, 450000.00, 2300000.00,
            'APPROVED', 'admin.tenant3', NOW() - INTERVAL '2 days', 'Recibo demo mensual emp2',
            DATE_TRUNC('month', CURRENT_DATE)::DATE,
            (DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')::DATE,
            NOW(), NOW()
        );
    END IF;
    IF v_emp2 IS NOT NULL THEN
        SELECT id INTO v_r2 FROM payroll_receipts
         WHERE company_id=v_company_id AND employee_id=v_emp2 AND period_year=v_year AND period_month=v_month
           AND deleted_at IS NULL LIMIT 1;

        IF v_r2 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM payroll_lines WHERE receipt_id=v_r2 AND deleted_at IS NULL) THEN
            INSERT INTO payroll_lines (company_id, receipt_id, concept_code, concept_name, line_type, line_order, amount, created_at) VALUES
            (v_company_id, v_r2, 'SALARIO_BASE',     'Salario base',             'EARNING',               1, 2500000.00, NOW()),
            (v_company_id, v_r2, 'SALUD_EMPLEADO',   'Aporte salud empleado',    'DEDUCTION',             2,  100000.00, NOW()),
            (v_company_id, v_r2, 'PENSION_EMPLEADO', 'Aporte pension empleado',  'DEDUCTION',             3,  100000.00, NOW()),
            (v_company_id, v_r2, 'SALUD_EMPRESA',    'Aporte salud empresa',     'EMPLOYER_CONTRIBUTION', 4,  212500.00, NOW()),
            (v_company_id, v_r2, 'PENSION_EMPRESA',  'Aporte pension empresa',   'EMPLOYER_CONTRIBUTION', 5,  237500.00, NOW());
        END IF;
    END IF;

    RAISE NOTICE 'V9-ZZ: payroll_receipts demo asegurados';
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'V9-ZZ [Bloque 10 NOM] FALLO: %', SQLERRM;
END $$;

-- =============================================================================
-- SMOKE CHECK: conteos finales en company_id=3
-- =============================================================================
DO $$
DECLARE r RECORD;
BEGIN
    RAISE NOTICE '=== V9-ZZ SMOKE CHECK (company_id=3) ===';
    FOR r IN
        SELECT 'checkbooks' AS tabla, COUNT(*)::TEXT AS n FROM checkbooks WHERE company_id=3 AND deleted_at IS NULL
        UNION ALL SELECT 'checks',              COUNT(*)::TEXT FROM checks              WHERE company_id=3 AND deleted_at IS NULL
        UNION ALL SELECT 'financial_movements', COUNT(*)::TEXT FROM financial_movements WHERE company_id=3
        UNION ALL SELECT 'dian_resolutions',    COUNT(*)::TEXT FROM dian_resolutions    WHERE company_id=3 AND deleted_at IS NULL
        UNION ALL SELECT 'sales_invoices',      COUNT(*)::TEXT FROM sales_invoices      WHERE company_id=3 AND deleted_at IS NULL
        UNION ALL SELECT 'sales_invoice_lines', COUNT(*)::TEXT FROM sales_invoice_lines WHERE company_id=3 AND deleted_at IS NULL
        UNION ALL SELECT 'ar_payments',         COUNT(*)::TEXT FROM ar_payments         WHERE company_id=3 AND deleted_at IS NULL
        UNION ALL SELECT 'purchase_orders',     COUNT(*)::TEXT FROM purchase_orders     WHERE company_id=3 AND deleted_at IS NULL
        UNION ALL SELECT 'purchase_order_lines',COUNT(*)::TEXT FROM purchase_order_lines WHERE company_id=3 AND deleted_at IS NULL
        UNION ALL SELECT 'goods_receipts',      COUNT(*)::TEXT FROM goods_receipts      WHERE company_id=3 AND deleted_at IS NULL
        UNION ALL SELECT 'goods_receipt_lines', COUNT(*)::TEXT FROM goods_receipt_lines WHERE company_id=3 AND deleted_at IS NULL
        UNION ALL SELECT 'invoices (AP)',       COUNT(*)::TEXT FROM invoices            WHERE company_id=3 AND deleted_at IS NULL
        UNION ALL SELECT 'lines_invoice',       COUNT(*)::TEXT FROM lines_invoice       WHERE company_id=3 AND deleted_at IS NULL
        UNION ALL SELECT 'ap_payments',         COUNT(*)::TEXT FROM ap_payments         WHERE company_id=3 AND deleted_at IS NULL
        UNION ALL SELECT 'payroll_receipts',    COUNT(*)::TEXT FROM payroll_receipts    WHERE company_id=3 AND deleted_at IS NULL
        UNION ALL SELECT 'payroll_lines',       COUNT(*)::TEXT FROM payroll_lines       WHERE company_id=3 AND deleted_at IS NULL
    LOOP
        RAISE NOTICE '  %: %', RPAD(r.tabla, 25), r.n;
    END LOOP;
END $$;
