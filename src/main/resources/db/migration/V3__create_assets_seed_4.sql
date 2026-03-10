CREATE TABLE IF NOT EXISTS assets (
    id BIGSERIAL PRIMARY KEY,
    asset_code VARCHAR(30) NOT NULL,
    asset_name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    classification VARCHAR(20) NOT NULL,
    asset_type VARCHAR(20) NOT NULL,
    chart_of_account_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    acquisition_value NUMERIC(19, 2) NOT NULL,
    acquisition_date DATE NOT NULL,
    useful_life_months INTEGER NOT NULL,
    depreciation_method VARCHAR(40) NOT NULL,
    payment_terms VARCHAR(120) NOT NULL,
    accounts_payable_reference_id BIGINT NULL,
    bank_cash_reference_id BIGINT NULL,
    cost_center_or_accounting_location VARCHAR(120),
    asset_status VARCHAR(30) NOT NULL,
    observations VARCHAR(500),
    created_by VARCHAR(150),
    updated_by VARCHAR(150),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_assets_chart_of_account
        FOREIGN KEY (chart_of_account_id) REFERENCES cfg_chart_of_accounts(id),
    CONSTRAINT fk_assets_supplier
        FOREIGN KEY (supplier_id) REFERENCES third_parties(id)
);

CREATE TABLE IF NOT EXISTS assets_audit_log (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    modified_by VARCHAR(150),
    change_summary TEXT,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_assets_audit_log_asset
        FOREIGN KEY (asset_id) REFERENCES assets(id)
);

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
