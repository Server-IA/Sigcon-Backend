-- Dependencias pendientes de modulos no implementados:
-- 1) Cuentas por pagar
-- ALTER TABLE assets
-- ADD CONSTRAINT fk_assets_accounts_payable
-- FOREIGN KEY (accounts_payable_reference_id) REFERENCES accounts_payable(id);
--
-- 2) Bancos / Cajas
-- ALTER TABLE assets
-- ADD CONSTRAINT fk_assets_bank_cash
-- FOREIGN KEY (bank_cash_reference_id) REFERENCES bank_cash_movements(id);

INSERT INTO assets (
    asset_code,
    asset_name,
    description,
    classification,
    asset_type,
    chart_of_account_id,
    supplier_id,
    acquisition_value,
    acquisition_date,
    useful_life_months,
    depreciation_method,
    payment_terms,
    accounts_payable_reference_id,
    bank_cash_reference_id,
    cost_center_or_accounting_location,
    asset_status,
    observations,
    created_by,
    updated_by,
    created_at,
    updated_at
)

SELECT
    'ACT2026000001',
    'IMPRESORA LASER OFICINA',
    'Activo fijo de oficina para operaciones administrativas.',
    'NON_CURRENT',
    'TANGIBLE',
    account_ref.id,
    supplier_ref.id,
    3200000.00,
    DATE '2026-01-15',
    60,
    'STRAIGHT_LINE',
    '30 dias',
    NULL,
    NULL,
    'Sede principal',
    'ACTIVE',
    'Seed inicial modulo assets',
    'seed-script',
    'seed-script',
    NOW(),
    NOW()
FROM
    (
        SELECT id
        FROM cfg_chart_of_accounts
        WHERE account_class = 'ASSET'
          AND account_status = 'ACTIVE'
          AND deleted_at IS NULL
        ORDER BY id
        LIMIT 1
    ) account_ref
CROSS JOIN
    (
        SELECT id
        FROM third_parties
        WHERE deleted_at IS NULL
        ORDER BY id
        LIMIT 1
    ) supplier_ref
WHERE NOT EXISTS (
    SELECT 1
    FROM assets
    WHERE asset_code = 'ACT2026000001'
      AND deleted_at IS NULL
);

INSERT INTO assets_audit_log (
    asset_id,
    action,
    modified_by,
    change_summary,
    notes,
    created_at,
    updated_at
)

SELECT
    a.id,
    'CREATE',
    'seed-script',
    'assetCode: [null] -> [ACT2026000001]',
    'Registro inicial generado por seed',
    NOW(),
    NOW()
FROM assets a
WHERE a.asset_code = 'ACT2026000001'
  AND NOT EXISTS (
      SELECT 1
      FROM assets_audit_log l
      WHERE l.asset_id = a.id
        AND l.action = 'CREATE'
  );
