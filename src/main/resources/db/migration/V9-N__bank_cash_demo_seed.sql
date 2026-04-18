-- V9-N: seed demo de 1 caja + 1 banco + 1 cuenta bancaria
-- Permite que el form de crear activo ofrezca opciones de "Origen de pago"
-- en BD limpia (Dokploy redeploy + local). Idempotente.

-- 1) Caja demo (petty cash) usando PUC 1105 "Caja general operativa"
INSERT INTO cash (
    cash_code, cash_name, cash_type, cash_status, accounting_book, audit_frequency,
    cash_creation_date, initial_balance_date, initial_balance, current_balance,
    physical_location, requires_authorization,
    accounting_account_id, currency_id, principal_responsible_id,
    created_at, updated_at
)
SELECT
    'CAJA-001', 'Caja General Principal', 'GENERAL', 'ACTIVE', 'LOCAL', 'MONTHLY',
    CURRENT_DATE, CURRENT_DATE, 0, 0,
    'Oficina principal', false,
    aa.id, c.id, u.id,
    NOW(), NOW()
FROM accounting_accounts aa
JOIN cfg_chart_of_accounts coa ON coa.id = aa.puc_id
JOIN cfg_currency_types c ON c.iso_code = 'COP'
JOIN users u ON u.username = 'superadmin' AND u.deleted_at IS NULL
WHERE coa.account_code = '1105' AND aa.deleted_at IS NULL AND c.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM cash WHERE cash_code = 'CAJA-001' AND deleted_at IS NULL)
LIMIT 1;

-- 2) Banco demo (Bancolombia)
INSERT INTO banks (
    code, code_ach, name, name_short, nit, swift, type_bank, status,
    country_id, created_at, updated_at
)
SELECT
    '007', '007', 'BANCOLOMBIA S.A.', 'Bancolombia', '890903938-8',
    'COLOCOBM', 'COMMERCIAL', 'ACTIVE',
    co.id, NOW(), NOW()
FROM countries co
WHERE co.name = 'COLOMBIA' AND co.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM banks WHERE code = '007' AND deleted_at IS NULL)
LIMIT 1;

-- 3) Cuenta bancaria demo (corriente) asociada al banco y al PUC 1110
INSERT INTO bank_accounts (
    code, account_name, account_number, account_type, status,
    initial_balance, allows_overdraft, handles_checkbook, notify_low_balance,
    accounting_account_id, bank_id, currency_type_id,
    created_at, updated_at
)
SELECT
    'BCO-001', 'Cuenta Corriente Operativa', '12345678901', 'CORRIENTE', 'ACTIVA',
    0, false, true, false,
    aa.id, b.id, c.id,
    NOW(), NOW()
FROM accounting_accounts aa
JOIN cfg_chart_of_accounts coa ON coa.id = aa.puc_id
JOIN banks b ON b.code = '007' AND b.deleted_at IS NULL
JOIN cfg_currency_types c ON c.iso_code = 'COP' AND c.deleted_at IS NULL
WHERE coa.account_code = '1110' AND aa.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM bank_accounts WHERE code = 'BCO-001' AND deleted_at IS NULL)
LIMIT 1;
