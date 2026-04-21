-- =============================================================================
-- V10-F: barrido final de UNIQUE parciales legacy -> compuestos con company_id
-- Fecha: 2026-04-19
-- Bloque G post-audit: tablas tenant-scoped que todavia tenian UNIQUE parcial
-- global (WHERE deleted_at IS NULL) sin incluir company_id. Cada empresa debe
-- poder tener sus propios codigos/nits/names sin colisionar cross-tenant.
-- =============================================================================

-- assets: asset_code
DROP INDEX IF EXISTS uk_assets_code_active;
CREATE UNIQUE INDEX IF NOT EXISTS uk_assets_company_code_active
    ON assets (company_id, asset_code) WHERE deleted_at IS NULL;

-- banks: name, nit, code, code_ach, name_short, swift
DROP INDEX IF EXISTS uk_banks_ach_active;
DROP INDEX IF EXISTS uk_banks_code_active;
DROP INDEX IF EXISTS uk_banks_name_active;
DROP INDEX IF EXISTS uk_banks_nit_active;
DROP INDEX IF EXISTS uk_banks_short_name_active;
DROP INDEX IF EXISTS uk_banks_swift_active;
CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_company_ach
    ON banks (company_id, code_ach) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_company_code
    ON banks (company_id, code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_company_name
    ON banks (company_id, name) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_company_nit
    ON banks (company_id, nit) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_company_short_name
    ON banks (company_id, name_short) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_company_swift
    ON banks (company_id, swift) WHERE deleted_at IS NULL;

-- bnk_cash_flow_projections (el nuevo uk_cashflow_company_name ya existe, solo drop legacy)
DROP INDEX IF EXISTS uidx_bnk_cfp_name_active;

-- commercial_data: client_id
DROP INDEX IF EXISTS uk_commercial_data_third_party_active;
CREATE UNIQUE INDEX IF NOT EXISTS uk_commercial_data_company_client
    ON commercial_data (company_id, client_id) WHERE deleted_at IS NULL;

-- depretation_rules: (type, account, effective_date)
DROP INDEX IF EXISTS uk_depretation_rule_type_accounting_account_effective_date_acti;
CREATE UNIQUE INDEX IF NOT EXISTS uk_dep_rule_company_type_acct_date
    ON depretation_rules (company_id, depretation_type, accounting_account_id, effective_date)
    WHERE deleted_at IS NULL;

-- employees: (document_type, document_number)
DROP INDEX IF EXISTS uk_employees_document;
CREATE UNIQUE INDEX IF NOT EXISTS uk_employees_company_document
    ON employees (company_id, document_type, document_number) WHERE deleted_at IS NULL;

-- integration_batches: (exchange_id, standard_version) — cada tenant tiene su flujo
DROP INDEX IF EXISTS ux_integration_batches_exchange_version;
CREATE UNIQUE INDEX IF NOT EXISTS uk_integration_batches_company_exchange
    ON integration_batches (company_id, exchange_id, standard_version) WHERE deleted_at IS NULL;

-- integration_idempotency_keys: (exchange_id, standard_version)
DROP INDEX IF EXISTS ux_idempotency_exchange_version;
CREATE UNIQUE INDEX IF NOT EXISTS uk_idempotency_company_exchange
    ON integration_idempotency_keys (company_id, exchange_id, standard_version);

-- parameters: name
DROP INDEX IF EXISTS uk_parameters_active;
CREATE UNIQUE INDEX IF NOT EXISTS uk_parameters_company_name
    ON parameters (company_id, name) WHERE deleted_at IS NULL;

-- payroll_concepts: code
DROP INDEX IF EXISTS uk_payroll_concepts_code;
CREATE UNIQUE INDEX IF NOT EXISTS uk_payroll_concepts_company_code
    ON payroll_concepts (company_id, code) WHERE deleted_at IS NULL;

-- risk_segmentation: el uk_risksegm_company_client ya existe (V10-D) solo limpiar legacy
DROP INDEX IF EXISTS uk_risk_segmentation_client_active;

-- system_withholding_assignments: withholding_id por tenant
DROP INDEX IF EXISTS uk_sys_wh_active;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_wh_company_active
    ON system_withholding_assignments (company_id, withholding_id)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

-- users: email y username siguen siendo globales (un email -> un user, sin importar empresa)
-- No se tocan uk_users_email_active, uk_users_username_active.
