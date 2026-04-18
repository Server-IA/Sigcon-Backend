-- PUC

-- INSERT INTO cfg_chart_of_accounts
-- (account_code, account_name, account_class, account_level, account_nature, account_status, created_at, updated_at)
-- SELECT '1', 'Activo', 'ASSET', 'CLASS', 'DEBIT', 'ACTIVE', NOW(), NOW()
-- WHERE NOT EXISTS (
--     SELECT 1 FROM cfg_chart_of_accounts 
--     WHERE account_code = '1' AND deleted_at IS NULL
-- );

-- INSERT INTO cfg_chart_of_accounts
-- (account_code, account_name, account_class, account_level, account_nature, account_status, created_at, updated_at)
-- SELECT '2', 'Pasivo', 'LIABILITY', 'CLASS', 'CREDIT', 'ACTIVE', NOW(), NOW()
-- WHERE NOT EXISTS (
--     SELECT 1 FROM cfg_chart_of_accounts 
--     WHERE account_code = '2' AND deleted_at IS NULL
-- );

-- INSERT INTO cfg_chart_of_accounts
-- (account_code, account_name, account_class, account_level, account_nature, account_status, created_at, updated_at)
-- SELECT '3', 'Patrimonio', 'EQUITY', 'CLASS', 'CREDIT', 'ACTIVE', NOW(), NOW()
-- WHERE NOT EXISTS (
--     SELECT 1 FROM cfg_chart_of_accounts 
--     WHERE account_code = '3' AND deleted_at IS NULL
-- );

-- Type Currency

INSERT INTO cfg_currency_types
(iso_code, name, status, created_at, updated_at)
SELECT 'COP', 'Peso colombiano', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM cfg_currency_types 
    WHERE iso_code = 'COP' AND deleted_at IS NULL
);


INSERT INTO cfg_currency_types
(iso_code, name, status, created_at, updated_at)
SELECT 'USD', 'Dólar estadounidense', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM cfg_currency_types 
    WHERE iso_code = 'USD' AND deleted_at IS NULL
);

INSERT INTO cfg_currency_types
(iso_code, name, status, created_at, updated_at)
SELECT 'EUR', 'Euro', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM cfg_currency_types 
    WHERE iso_code = 'EUR' AND deleted_at IS NULL
);

-- COST CENTER

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'cost_centers' AND column_name = 'company_id') THEN
        INSERT INTO cost_centers (code, name, description, status, company_id, created_at, updated_at)
        SELECT '1', 'Centro de costo 1', 'Descripcion del centro de costo 1', 'ACTIVE', 1, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM cost_centers WHERE code = '1' AND company_id = 1 AND deleted_at IS NULL);
    ELSE
        INSERT INTO cost_centers (code, name, description, status, created_at, updated_at)
        SELECT '1', 'Centro de costo 1', 'Descripcion del centro de costo 1', 'ACTIVE', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM cost_centers WHERE code = '1' AND deleted_at IS NULL);
    END IF;
END $$;

-- Accounting Account

-- INSERT INTO accounting_accounts
-- (puc_id, custom_name, currency_type_id, cost_center_id, tax_rule_id, nature, status, company_id, created_at, updated_at)
-- VALUES
-- ('1', 'Cuenta de banco', '1', '1', '1', 'DEBIT', 'ACTIVE', 1, NOW(), NOW()),
-- ('2', 'Cuenta de caja', '2', '2', '2', 'CREDIT', 'ACTIVE', 1, NOW(), NOW()),
-- ('3', 'Cuenta de clientes', '3', '3', '3', 'DEBIT', 'ACTIVE', 1, NOW(), NOW());
