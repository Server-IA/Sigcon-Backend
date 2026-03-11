-- PUC

INSERT INTO cfg_chart_of_accounts
(account_code, account_name, account_class, account_level, account_nature, account_status, created_at, updated_at)
VALUES
('1', 'Activo', 'ASSET', 'CLASS', 'DEBIT', 'ACTIVE', NOW(), NOW()),
('2', 'Pasivo', 'LIABILITY', 'CLASS', 'CREDIT', 'ACTIVE', NOW(), NOW()),
('3', 'Patrimonio', 'EQUITY', 'CLASS', 'CREDIT', 'ACTIVE', NOW(), NOW());

INSERT INTO cfg_chart_of_accounts
(account_code, account_name, account_class, account_level, account_nature, account_status, created_at, updated_at)
VALUES
('11', 'Disponible', 'ASSET', 'GROUP', 'DEBIT', 'ACTIVE', NOW(), NOW()),
('12', 'Inversiones', 'ASSET', 'GROUP', 'DEBIT', 'ACTIVE', NOW(), NOW()),
('13', 'Deudores', 'ASSET', 'GROUP', 'DEBIT', 'ACTIVE', NOW(), NOW()),
('14', 'Inventarios', 'ASSET', 'GROUP', 'DEBIT', 'ACTIVE', NOW(), NOW()),
('15', 'Propiedad planta y equipo', 'ASSET', 'GROUP', 'DEBIT', 'ACTIVE', NOW(), NOW()),

('21', 'Obligaciones financieras', 'LIABILITY', 'GROUP', 'CREDIT', 'ACTIVE', NOW(), NOW()),
('22', 'Proveedores', 'LIABILITY', 'GROUP', 'CREDIT', 'ACTIVE', NOW(), NOW()),
('23', 'Cuentas por pagar', 'LIABILITY', 'GROUP', 'CREDIT', 'ACTIVE', NOW(), NOW()),
('24', 'Impuestos gravámenes y tasas', 'LIABILITY', 'GROUP', 'CREDIT', 'ACTIVE', NOW(), NOW()),

('31', 'Capital social', 'EQUITY', 'GROUP', 'CREDIT', 'ACTIVE', NOW(), NOW()),
('32', 'Superávit de capital', 'EQUITY', 'GROUP', 'CREDIT', 'ACTIVE', NOW(), NOW()),
('33', 'Reservas', 'EQUITY', 'GROUP', 'CREDIT', 'ACTIVE', NOW(), NOW());

-- Type Currency

INSERT INTO cfg_currency_types
(iso_code, name, status, created_at, updated_at)
VALUES
('USD', 'Dólar estadounidense', 'ACTIVE', NOW(), NOW()),
('EUR', 'Euro', 'ACTIVE', NOW(), NOW()),
('GBP', 'Libra esterlina', 'ACTIVE', NOW(), NOW()),
('JPY', 'Yen japonés', 'ACTIVE', NOW(), NOW()),
('KRW', 'Won surcoreano', 'ACTIVE', NOW(), NOW());

-- COST CENTER

INSERT INTO cost_centers
(code, name, description, status, company_id, created_at, updated_at)
VALUES
('1', 'Centro de costo 1', 'Descripción del centro de costo 1', 'ACTIVE', 1, NOW(), NOW()),
('2', 'Centro de costo 2', 'Descripción del centro de costo 2', 'ACTIVE', 1, NOW(), NOW()),
('3', 'Centro de costo 3', 'Descripción del centro de costo 3', 'ACTIVE', 1, NOW(), NOW());

-- Accounting Account

-- INSERT INTO accounting_accounts
-- (puc_id, custom_name, currency_type_id, cost_center_id, tax_rule_id, nature, status, company_id, created_at, updated_at)
-- VALUES
-- ('1', 'Cuenta de banco', '1', '1', '1', 'DEBIT', 'ACTIVE', 1, NOW(), NOW()),
-- ('2', 'Cuenta de caja', '2', '2', '2', 'CREDIT', 'ACTIVE', 1, NOW(), NOW()),
-- ('3', 'Cuenta de clientes', '3', '3', '3', 'DEBIT', 'ACTIVE', 1, NOW(), NOW());
