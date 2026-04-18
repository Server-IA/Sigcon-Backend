-- ============================================================================
-- V17: Eliminacion del modelo multi-tenant
-- Migra datos de empresa a tabla parameters y elimina company_id de entidades
-- Fecha: 2026-04-12
--
-- IDEMPOTENCIA Fase 3 (2026-04-14):
--   Tras la eliminacion definitiva de la entidad Company (Fase 3), la tabla
--   'companies' puede no existir. Los bloques que leen FROM companies se
--   envuelven en un guard que verifica la existencia de la tabla. Las
--   operaciones ALTER TABLE ... DROP COLUMN IF EXISTS son inherentemente
--   seguras. Los seeds de parametros COMPANY_* por defecto (parte final) se
--   mantienen como fallback para asegurar datos minimos.
-- ============================================================================

-- 1. Insertar datos de la empresa existente en tabla parameters (solo si tabla companies existe)
-- ============================================================================
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'companies' AND table_schema = 'public'
    ) THEN
        INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
        SELECT 'COMPANY_NAME', c.name, 'Razon social de la empresa', 'COMPANY', 'ACTIVE', NOW(), NOW()
        FROM companies c WHERE c.deleted_at IS NULL LIMIT 1
        ON CONFLICT DO NOTHING;

        INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
        SELECT 'COMPANY_NIT', c.nit, 'NIT de la empresa', 'COMPANY', 'ACTIVE', NOW(), NOW()
        FROM companies c WHERE c.deleted_at IS NULL LIMIT 1
        ON CONFLICT DO NOTHING;

        INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
        SELECT 'COMPANY_DV', c.dv, 'Digito de verificacion', 'COMPANY', 'ACTIVE', NOW(), NOW()
        FROM companies c WHERE c.deleted_at IS NULL LIMIT 1
        ON CONFLICT DO NOTHING;

        INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
        SELECT 'COMPANY_LEGAL_REPRESENTATIVE', c.legal_representative, 'Representante legal', 'COMPANY', 'ACTIVE', NOW(), NOW()
        FROM companies c WHERE c.deleted_at IS NULL LIMIT 1
        ON CONFLICT DO NOTHING;

        INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
        SELECT 'COMPANY_EMAIL', c.email, 'Email de la empresa', 'COMPANY', 'ACTIVE', NOW(), NOW()
        FROM companies c WHERE c.deleted_at IS NULL LIMIT 1
        ON CONFLICT DO NOTHING;

        INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
        SELECT 'COMPANY_SIZE', c.size, 'Tamano de la empresa', 'COMPANY', 'ACTIVE', NOW(), NOW()
        FROM companies c WHERE c.deleted_at IS NULL LIMIT 1
        ON CONFLICT DO NOTHING;

        INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
        SELECT 'COMPANY_PHONE', c.phone, 'Telefono de la empresa', 'COMPANY', 'ACTIVE', NOW(), NOW()
        FROM companies c WHERE c.deleted_at IS NULL LIMIT 1
        ON CONFLICT DO NOTHING;

        INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
        SELECT 'COMPANY_TYPE_REGIMEN_ID', CAST(c.type_regimen_id AS VARCHAR), 'ID del tipo de regimen tributario', 'COMPANY', 'ACTIVE', NOW(), NOW()
        FROM companies c WHERE c.deleted_at IS NULL LIMIT 1
        ON CONFLICT DO NOTHING;

        INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
        SELECT 'COMPANY_TYPE_ORGANIZATION_ID', CAST(c.type_organization_id AS VARCHAR), 'ID del tipo de organizacion', 'COMPANY', 'ACTIVE', NOW(), NOW()
        FROM companies c WHERE c.deleted_at IS NULL LIMIT 1
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

-- Insertar datos por defecto si la tabla companies no existe o esta vacia
-- Estos siempre corren como fallback (garantiza que existan los parametros COMPANY_*)
INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
SELECT 'COMPANY_NAME', 'Sigcon S.A.S.', 'Razon social de la empresa', 'COMPANY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name = 'COMPANY_NAME' AND deleted_at IS NULL);

INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
SELECT 'COMPANY_NIT', '9001234567', 'NIT de la empresa', 'COMPANY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name = 'COMPANY_NIT' AND deleted_at IS NULL);

INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
SELECT 'COMPANY_DV', '1', 'Digito de verificacion', 'COMPANY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name = 'COMPANY_DV' AND deleted_at IS NULL);

INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
SELECT 'COMPANY_LEGAL_REPRESENTATIVE', 'Representante Legal', 'Representante legal', 'COMPANY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name = 'COMPANY_LEGAL_REPRESENTATIVE' AND deleted_at IS NULL);

INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
SELECT 'COMPANY_EMAIL', 'empresa@sigcon.co', 'Email de la empresa', 'COMPANY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name = 'COMPANY_EMAIL' AND deleted_at IS NULL);

INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
SELECT 'COMPANY_SIZE', '100', 'Tamano de la empresa', 'COMPANY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name = 'COMPANY_SIZE' AND deleted_at IS NULL);

INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
SELECT 'COMPANY_PHONE', '0000000000', 'Telefono de la empresa', 'COMPANY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name = 'COMPANY_PHONE' AND deleted_at IS NULL);

INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
SELECT 'COMPANY_TYPE_REGIMEN_ID', '2', 'ID del tipo de regimen tributario', 'COMPANY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name = 'COMPANY_TYPE_REGIMEN_ID' AND deleted_at IS NULL);

INSERT INTO parameters (name, value, description, category, status, created_at, updated_at)
SELECT 'COMPANY_TYPE_ORGANIZATION_ID', '1', 'ID del tipo de organizacion', 'COMPANY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name = 'COMPANY_TYPE_ORGANIZATION_ID' AND deleted_at IS NULL);


-- 2. Eliminar columnas company_id de todas las entidades afectadas
-- ALTER TABLE ... DROP COLUMN IF EXISTS es seguro aun si la columna ya fue removida
-- ============================================================================

-- Assets
ALTER TABLE assets DROP CONSTRAINT IF EXISTS fk_assets_company;
ALTER TABLE assets DROP COLUMN IF EXISTS company_id;

-- Bank accounts
ALTER TABLE bank_accounts DROP CONSTRAINT IF EXISTS fk_bank_accounts_company;
ALTER TABLE bank_accounts DROP COLUMN IF EXISTS company_id;

-- Financial movements
ALTER TABLE financial_movements DROP CONSTRAINT IF EXISTS fk_financial_movements_company;
ALTER TABLE financial_movements DROP COLUMN IF EXISTS company_id;

-- Bank reconciliation sessions
ALTER TABLE bank_reconciliation_sessions DROP CONSTRAINT IF EXISTS fk_bank_reconciliation_sessions_company;
ALTER TABLE bank_reconciliation_sessions DROP COLUMN IF EXISTS company_id;

-- Invoices (company_id + location_origin_id + location_destination_id)
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS fk_invoices_company;
ALTER TABLE invoices DROP COLUMN IF EXISTS company_id;
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS fk_invoices_location_origin;
ALTER TABLE invoices DROP COLUMN IF EXISTS location_origin_id;
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS fk_invoices_location_destination;
ALTER TABLE invoices DROP COLUMN IF EXISTS location_destination_id;

-- Accounting accounts
ALTER TABLE accounting_accounts DROP CONSTRAINT IF EXISTS fk_accounting_accounts_company;
ALTER TABLE accounting_accounts DROP COLUMN IF EXISTS company_id;

-- Users (nullable FK)
ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_users_company;
ALTER TABLE users DROP COLUMN IF EXISTS company_id;

-- Vouchers
ALTER TABLE vouchers DROP CONSTRAINT IF EXISTS fk_vouchers_company;
ALTER TABLE vouchers DROP COLUMN IF EXISTS company_id;

-- Cost centers
ALTER TABLE cost_centers DROP COLUMN IF EXISTS company_id;

-- Exchange rates
ALTER TABLE exchange_rates DROP COLUMN IF EXISTS company_id;


-- 3. Eliminar tabla company_locations (safe con IF EXISTS)
-- ============================================================================
DROP TABLE IF EXISTS company_locations CASCADE;


-- 4. Eliminar permisos de empresa del sistema
-- ============================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'roles_permissions') THEN
        DELETE FROM roles_permissions WHERE permission_id IN (
            SELECT id FROM permissions WHERE code IN (
                'PERM_CREATE_COMPANY', 'PERM_VIEW_COMPANY',
                'PERM_UPDATE_COMPANY', 'PERM_DELETE_COMPANY',
                'PERM_CREATE_COMPANY_LOCATION', 'PERM_VIEW_COMPANY_LOCATION',
                'PERM_UPDATE_COMPANY_LOCATION', 'PERM_DELETE_COMPANY_LOCATION'
            )
        );
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'permissions') THEN
        DELETE FROM permissions WHERE code IN (
            'PERM_CREATE_COMPANY', 'PERM_VIEW_COMPANY',
            'PERM_UPDATE_COMPANY', 'PERM_DELETE_COMPANY',
            'PERM_CREATE_COMPANY_LOCATION', 'PERM_VIEW_COMPANY_LOCATION',
            'PERM_UPDATE_COMPANY_LOCATION', 'PERM_DELETE_COMPANY_LOCATION'
        );
    END IF;
END $$;


-- 5. Cambiar rol SUPERADMIN a ADMIN (safe; no-op si no existe)
-- ============================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'roles') THEN
        UPDATE roles SET name = 'ADMIN' WHERE name = 'SUPERADMIN';
    END IF;
END $$;
