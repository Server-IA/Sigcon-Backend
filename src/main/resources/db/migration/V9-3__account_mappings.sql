-- V31: Mapeo de conceptos contables a cuentas PUC reales.
-- Resuelve la deuda tecnica de usar fallback/hardcoded account IDs en los
-- JournalEntry generados automaticamente por AR, AP, BNK, ACT.
--
-- Estrategia:
-- 1. Crear tabla account_mappings (concept_code -> accounting_account_id)
-- 2. Para cada concepto, si no existe un accounting_account apuntando al PUC
--    correspondiente, se crea uno automaticamente.
-- 3. Insertar el mapeo en account_mappings.
--
-- Referencias PUC Colombia (Decreto 2650/1993):
--   1305 Clientes (CxC)
--   1330 Anticipos y avances a proveedores
--   1355 Anticipo de impuestos y contribuciones (retenciones practicadas a nosotros)
--   2205 Proveedores
--   2365 Retencion en la fuente (retenciones que practicamos)
--   2408 Impuesto a las ventas por pagar
--   2805 Anticipos y avances recibidos de clientes
--   4135 Comercio al por mayor y al por menor
--   4215 Diferencia en cambio (ingreso)
--   5305 Gastos no operacionales - financieros (diferencia en cambio gasto)
--   1110 Bancos
--   1105 Caja

-- ==========================================================================
-- 1. Tabla de mapeo
-- ==========================================================================
CREATE TABLE IF NOT EXISTS account_mappings (
    id BIGSERIAL PRIMARY KEY,
    concept_code VARCHAR(64) NOT NULL,
    concept_description VARCHAR(255) NOT NULL,
    puc_code VARCHAR(10) NOT NULL,
    accounting_account_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_account_mappings_account
        FOREIGN KEY (accounting_account_id) REFERENCES accounting_accounts(id)
);

-- V10-D reemplaza este UNIQUE simple por (company_id, concept_code) — ver V10-D.
-- Legacy sin tenant, neutralizado aqui para evitar colisiones al re-ejecutarse.
-- CREATE UNIQUE INDEX IF NOT EXISTS ux_account_mappings_concept
--     ON account_mappings(concept_code) WHERE deleted_at IS NULL;

-- ==========================================================================
-- 2. Funcion auxiliar: asegurar que existe un accounting_account para un PUC
--    y devolver su id. Si no existe, lo crea.
-- ==========================================================================
DO $$
DECLARE
    v_currency_id BIGINT;
BEGIN
    -- Resolver moneda default (COP)
    SELECT id INTO v_currency_id
    FROM cfg_currency_types
    WHERE deleted_at IS NULL
    ORDER BY id
    LIMIT 1;

    IF v_currency_id IS NULL THEN
        RAISE EXCEPTION 'V31: No existe ninguna moneda en cfg_currency_types. No se pueden sembrar cuentas contables.';
    END IF;

    -- Funcion interna (expresada como bloques repetidos) para cada PUC requerido.
    -- Si no existe accounting_account apuntando al PUC, lo crea con custom_name del PUC.

    PERFORM 1; -- placeholder
END $$;

-- ==========================================================================
-- 3. Semilla: crear accounting_accounts faltantes para los 12 conceptos.
--    Cada INSERT es idempotente (WHERE NOT EXISTS).
-- ==========================================================================

-- Funcion auxiliar reutilizable
CREATE OR REPLACE FUNCTION ensure_accounting_account_for_puc(
    p_puc_code VARCHAR,
    p_custom_name VARCHAR,
    p_nature VARCHAR
) RETURNS BIGINT AS $$
DECLARE
    v_puc_id BIGINT;
    v_account_id BIGINT;
    v_currency_id BIGINT;
BEGIN
    -- Buscar el PUC
    SELECT id INTO v_puc_id
    FROM cfg_chart_of_accounts
    WHERE account_code = p_puc_code
      AND deleted_at IS NULL
    ORDER BY id
    LIMIT 1;

    IF v_puc_id IS NULL THEN
        RAISE EXCEPTION 'V31: PUC % no existe en cfg_chart_of_accounts', p_puc_code;
    END IF;

    -- Verificar si ya existe accounting_account para ese PUC
    SELECT id INTO v_account_id
    FROM accounting_accounts
    WHERE puc_id = v_puc_id
      AND deleted_at IS NULL
    ORDER BY id
    LIMIT 1;

    IF v_account_id IS NOT NULL THEN
        RETURN v_account_id;
    END IF;

    -- Resolver moneda default
    SELECT id INTO v_currency_id
    FROM cfg_currency_types
    WHERE deleted_at IS NULL
    ORDER BY id
    LIMIT 1;

    -- Crear accounting_account
    INSERT INTO accounting_accounts (
        puc_id, custom_name, currency_type_id, cost_center_id,
        tax_rule_id, nature, status, created_at, updated_at, created_by
    ) VALUES (
        v_puc_id,
        LEFT(p_custom_name, 50),
        v_currency_id,
        NULL, NULL,
        p_nature, 'ACTIVE', NOW(), NOW(), NULL
    ) RETURNING id INTO v_account_id;

    RETURN v_account_id;
END;
$$ LANGUAGE plpgsql;

-- ==========================================================================
-- 4. Insertar mapeos (idempotente via ON CONFLICT DO NOTHING)
-- ==========================================================================
INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'AR_CLIENTES', 'Cuentas por cobrar clientes', '1305',
       ensure_accounting_account_for_puc('1305', 'Clientes (CxC)', 'DEBIT'), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'AR_CLIENTES' AND deleted_at IS NULL);

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'AR_ANTICIPOS', 'Anticipos recibidos de clientes', '2805',
       ensure_accounting_account_for_puc('2805', 'Anticipos recibidos clientes', 'CREDIT'), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'AR_ANTICIPOS' AND deleted_at IS NULL);

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'AR_RET_PRACTICADAS_CLIENTE', 'Retenciones practicadas por clientes', '1355',
       ensure_accounting_account_for_puc('1355', 'Anticipo de impuestos', 'DEBIT'), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'AR_RET_PRACTICADAS_CLIENTE' AND deleted_at IS NULL);

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'AR_INGRESOS', 'Ingresos operacionales por ventas', '4135',
       ensure_accounting_account_for_puc('4135', 'Ingresos comercio', 'CREDIT'), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'AR_INGRESOS' AND deleted_at IS NULL);

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'AR_IVA_GENERADO', 'IVA generado en ventas', '2408',
       ensure_accounting_account_for_puc('2408', 'IVA por pagar', 'CREDIT'), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'AR_IVA_GENERADO' AND deleted_at IS NULL);

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'AP_PROVEEDORES', 'Cuentas por pagar proveedores', '2205',
       ensure_accounting_account_for_puc('2205', 'Proveedores', 'CREDIT'), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'AP_PROVEEDORES' AND deleted_at IS NULL);

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'AP_ANTICIPOS', 'Anticipos entregados a proveedores', '1330',
       ensure_accounting_account_for_puc('1330', 'Anticipos a proveedores', 'DEBIT'), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'AP_ANTICIPOS' AND deleted_at IS NULL);

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'AP_RET_PRACTICADAS', 'Retenciones practicadas en la fuente', '2365',
       ensure_accounting_account_for_puc('2365', 'Retenciones en la fuente', 'CREDIT'), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'AP_RET_PRACTICADAS' AND deleted_at IS NULL);

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'AP_IVA_DESCONTABLE', 'IVA descontable en compras', '2408',
       ensure_accounting_account_for_puc('2408', 'IVA por pagar', 'CREDIT'), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'AP_IVA_DESCONTABLE' AND deleted_at IS NULL);

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'BANCOS_DEFAULT', 'Bancos default (si movimiento no especifica banco)', '1110',
       ensure_accounting_account_for_puc('1110', 'Bancos', 'DEBIT'), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'BANCOS_DEFAULT' AND deleted_at IS NULL);

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'CAJA_DEFAULT', 'Caja default (si movimiento no especifica caja)', '1105',
       ensure_accounting_account_for_puc('1105', 'Caja', 'DEBIT'), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'CAJA_DEFAULT' AND deleted_at IS NULL);

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'DIF_CAMBIO_INGRESO', 'Ingreso por diferencia en cambio', '4215',
       ensure_accounting_account_for_puc('4215', 'Diferencia en cambio', 'CREDIT'), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'DIF_CAMBIO_INGRESO' AND deleted_at IS NULL);

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'DIF_CAMBIO_GASTO', 'Gasto por diferencia en cambio', '5305',
       ensure_accounting_account_for_puc('5305', 'Financieros diferencia cambio', 'DEBIT'), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'DIF_CAMBIO_GASTO' AND deleted_at IS NULL);

-- ==========================================================================
-- 5. Limpieza: eliminar la funcion auxiliar (no es necesaria en runtime)
-- ==========================================================================
DROP FUNCTION IF EXISTS ensure_accounting_account_for_puc(VARCHAR, VARCHAR, VARCHAR);
