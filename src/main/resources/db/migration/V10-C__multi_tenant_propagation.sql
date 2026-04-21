-- =============================================================================
-- V10-C: Multi-tenant propagation a todas las entidades tenant-scoped
-- Fecha: 2026-04-19
-- Bloque C - Propaga company_id a ~55 tablas del dominio que faltaban tras V10-A/V10-B.
--
-- Estrategia por tabla:
--   1. ADD COLUMN IF NOT EXISTS company_id BIGINT NULL (idempotente)
--   2. Backfill UPDATE ... SET company_id = 1 (SIGCON DEMO) si estaba NULL
--      (o heredar del parent si es tabla hija)
--   3. ALTER COLUMN SET NOT NULL
--   4. FK a companies(id)
--   5. Index para queries con @Filter
--
-- GLOBAL (NO tocar): companies, users, parameters (ya V10-A), catalogos (PUC,
-- monedas, paises, municipios, roles, permissions, modules, menus,
-- menu_permissions, report_types, report_templates, payment_terms,
-- type_organization, type_regimen, withholdings, invoice_states,
-- types_invoices, payment_forms, third_party_role_catalog,
-- third_party_status_catalog, voucher_types, payroll_retention_brackets,
-- blacklisted_tokens, password_reset_tokens, user_parameters).
-- =============================================================================

-- Funcion helper para no repetir el patron 55 veces.
CREATE OR REPLACE FUNCTION _mt_add_company(tbl text) RETURNS void AS $$
BEGIN
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS company_id BIGINT NULL', tbl);
    EXECUTE format('UPDATE %I SET company_id = 1 WHERE company_id IS NULL', tbl);
    EXECUTE format('ALTER TABLE %I ALTER COLUMN company_id SET NOT NULL', tbl);
    BEGIN
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT fk_%s_company FOREIGN KEY (company_id) REFERENCES companies(id)', tbl, tbl);
    EXCEPTION WHEN duplicate_object THEN NULL;
    END;
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_company ON %I(company_id)', tbl, tbl);
END;
$$ LANGUAGE plpgsql;

-- Variante para tablas hijas: hereda company_id del padre antes de set NOT NULL.
CREATE OR REPLACE FUNCTION _mt_add_company_child(child text, parent_fk text, parent_tbl text) RETURNS void AS $$
BEGIN
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS company_id BIGINT NULL', child);
    EXECUTE format('UPDATE %I c SET company_id = p.company_id FROM %I p WHERE c.%I = p.id AND c.company_id IS NULL',
                   child, parent_tbl, parent_fk);
    EXECUTE format('UPDATE %I SET company_id = 1 WHERE company_id IS NULL', child);
    EXECUTE format('ALTER TABLE %I ALTER COLUMN company_id SET NOT NULL', child);
    BEGIN
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT fk_%s_company FOREIGN KEY (company_id) REFERENCES companies(id)', child, child);
    EXCEPTION WHEN duplicate_object THEN NULL;
    END;
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_company ON %I(company_id)', child, child);
END;
$$ LANGUAGE plpgsql;

-- ============= TER (children, parent ya tiene en V10-A) ============
SELECT _mt_add_company_child('third_contact', 'third_party_id', 'third_parties');
SELECT _mt_add_company_child('third_party_withholding_assignments', 'third_party_id', 'third_parties');
SELECT _mt_add_company_child('third_party_role_assignments_v2', 'third_party_id', 'third_parties');
SELECT _mt_add_company_child('third_party_bank_accounts', 'third_party_id', 'third_parties');
SELECT _mt_add_company_child('third_party_change_history', 'third_party_id', 'third_parties');
SELECT _mt_add_company('commercial_data');
SELECT _mt_add_company_child('commercial_data_history', 'commercial_data_id', 'commercial_data');
SELECT _mt_add_company('risk_segmentation');
-- risk_segmentation_history no tiene FK a risk_segmentation; backfill simple
SELECT _mt_add_company('risk_segmentation_history');

-- ============= CG missing (cg_closing_entries) =============
SELECT _mt_add_company('cg_closing_entries');

-- ============= CFG (per-empresa) =============
SELECT _mt_add_company('exchange_rates');
SELECT _mt_add_company('ruler_tax');
SELECT _mt_add_company_child('tax_ruler_accounts', 'ruler_tax_id', 'ruler_tax');
SELECT _mt_add_company('depretation_rules');
SELECT _mt_add_company('account_mappings');
SELECT _mt_add_company('system_withholding_assignments');

-- ============= AP =============
SELECT _mt_add_company('invoices');
SELECT _mt_add_company_child('lines_invoice', 'invoice_id', 'invoices');
SELECT _mt_add_company('ap_payments');
SELECT _mt_add_company('ap_advances');
SELECT _mt_add_company('ap_credit_debit_notes');
SELECT _mt_add_company('invoice_attachments');
SELECT _mt_add_company('purchase_orders');
SELECT _mt_add_company_child('purchase_order_lines', 'purchase_order_id', 'purchase_orders');
SELECT _mt_add_company('goods_receipts');
SELECT _mt_add_company_child('goods_receipt_lines', 'goods_receipt_id', 'goods_receipts');

-- ============= AR =============
SELECT _mt_add_company('sales_invoices');
SELECT _mt_add_company_child('sales_invoice_lines', 'invoice_id', 'sales_invoices');
SELECT _mt_add_company('ar_payments');
SELECT _mt_add_company('ar_advances');
SELECT _mt_add_company('ar_credit_debit_notes');
SELECT _mt_add_company('sales_invoice_attachments');
SELECT _mt_add_company('dian_resolutions');
SELECT _mt_add_company('dian_invoice_submissions');

-- ============= BNK =============
SELECT _mt_add_company('banks');
SELECT _mt_add_company_child('bank_branches', 'bank_id', 'banks');
SELECT _mt_add_company('bank_accounts');
SELECT _mt_add_company('checkbooks');
SELECT _mt_add_company('checks');
SELECT _mt_add_company('cash');
SELECT _mt_add_company('cash_audits');
SELECT _mt_add_company('financial_movements');
SELECT _mt_add_company('bank_reconciliation_sessions');
SELECT _mt_add_company('bnk_cash_flow_projections');

-- ============= ACT =============
SELECT _mt_add_company('assets');
SELECT _mt_add_company_child('assets_taxes_retention', 'asset_id', 'assets');
SELECT _mt_add_company_child('assets_depreciation', 'asset_id', 'assets');
SELECT _mt_add_company('asset_disposals');
SELECT _mt_add_company('asset_annual_reviews');
SELECT _mt_add_company('niif_alerts');
SELECT _mt_add_company('niif_corrections');
SELECT _mt_add_company('niif_verifications');

-- ============= NOM =============
SELECT _mt_add_company('employees');
SELECT _mt_add_company_child('employee_salary_history', 'employee_id', 'employees');
SELECT _mt_add_company('payroll_concepts');
SELECT _mt_add_company('payroll_receipts');
SELECT _mt_add_company_child('payroll_lines', 'receipt_id', 'payroll_receipts');

-- ============= INT =============
SELECT _mt_add_company('integration_batches');
SELECT _mt_add_company_child('integration_transfers', 'batch_id', 'integration_batches');
SELECT _mt_add_company('integration_idempotency_keys');
SELECT _mt_add_company_child('integration_transfer_history', 'transfer_id', 'integration_transfers');
SELECT _mt_add_company('jwt_audit_log');

-- ============= AU =============
SELECT _mt_add_company('audit_logs');
SELECT _mt_add_company('audit_purge_records');
SELECT _mt_add_company('audit_retention_policies');
SELECT _mt_add_company('audit_risk_rules');

-- ============= Vouchers legacy =============
SELECT _mt_add_company('vouchers');

-- Cleanup de funciones helper
DROP FUNCTION _mt_add_company(text);
DROP FUNCTION _mt_add_company_child(text, text, text);
