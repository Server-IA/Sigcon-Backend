-- V9-M: Promover cuentas PPE principales a accounting_accounts + reglas de depreciacion.
--
-- El seed inicial solo crea la cuenta 1528 (Equipo de computacion). Para
-- permitir clasificar distintos tipos de activos fijos (terrenos, edificios,
-- maquinaria, vehiculos, etc.) se promueven las principales cuentas del
-- grupo 15 del PUC colombiano a accounting_accounts y se genera una regla
-- de depreciacion lineal por defecto para cada una (vida util segun NIC 16).
--
-- Idempotente: WHERE NOT EXISTS en ambos INSERT evita duplicados.
-- Terrenos (1504) se incluye pero sin regla de depreciacion (no se depreciable).

-- 1. Promover cuentas PUC a accounting_accounts
INSERT INTO accounting_accounts(custom_name, nature, status, currency_type_id, puc_id, created_at, updated_at)
SELECT x.name, 'DEBIT', 'ACTIVE', cop.id, c.id, NOW(), NOW()
FROM (VALUES
    ('1504','Terrenos'),
    ('1516','Construcciones y edificaciones'),
    ('1520','Maquinaria y equipo'),
    ('1524','Equipo de oficina'),
    ('1532','Equipo medico-cientifico'),
    ('1540','Flota y equipo de transporte'),
    ('1560','Armamento de vigilancia')
) AS x(code, name)
JOIN cfg_chart_of_accounts c ON c.account_code = x.code AND c.deleted_at IS NULL
CROSS JOIN LATERAL (
    SELECT id FROM cfg_currency_types WHERE iso_code = 'COP' AND deleted_at IS NULL LIMIT 1
) cop
WHERE NOT EXISTS (
    SELECT 1 FROM accounting_accounts a
    WHERE a.puc_id = c.id AND a.deleted_at IS NULL
);

-- 2. Crear regla de depreciacion lineal por cada cuenta PPE promovida.
--    Terrenos (1504) se excluye porque no es depreciable.
INSERT INTO depretation_rules(name, depretation_type, depretation_rate, residual_value,
        useful_life_years, description_structured, status, effective_date,
        accounting_account_id, created_at, updated_at)
SELECT
    a.custom_name || ' - Lineal ' || x.life || ' anios',
    'LINEAR',
    (100.0 / x.life)::NUMERIC(5,2),
    x.residual,
    x.life,
    'Depreciacion lineal para ' || a.custom_name || ' segun NIC 16. Vida util ' || x.life || ' anios.',
    'ACTIVE',
    '2020-01-01',
    a.id,
    NOW(), NOW()
FROM (VALUES
    ('1516', 20, 10),
    ('1520', 10, 5),
    ('1524', 10, 0),
    ('1532', 10, 5),
    ('1540', 5, 10),
    ('1560', 10, 0)
) AS x(code, life, residual)
JOIN cfg_chart_of_accounts c ON c.account_code = x.code AND c.deleted_at IS NULL
JOIN accounting_accounts a ON a.puc_id = c.id AND a.deleted_at IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM depretation_rules d
    WHERE d.accounting_account_id = a.id
      AND d.depretation_type = 'LINEAR'
      AND d.effective_date = '2020-01-01'
      AND d.deleted_at IS NULL
);
