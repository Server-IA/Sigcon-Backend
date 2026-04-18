-- ============================================================================
-- V22: Mejoras al modulo de Terceros
-- Cubre HUs: TER-03, TER-04, TER-05, TER-10, TER-11, TER-12
-- Fecha: 2026-04-13
-- ============================================================================

-- 1. ThirdParty: campo justificacion de eliminacion (TER-10)
-- ============================================================================
ALTER TABLE third_parties ADD COLUMN IF NOT EXISTS deleted_reason VARCHAR(500);

-- 2. Historial de cambios de terceros (TER-03)
-- ============================================================================
CREATE TABLE IF NOT EXISTS third_party_change_history (
    id BIGSERIAL PRIMARY KEY,
    third_party_id BIGINT NOT NULL REFERENCES third_parties(id),
    field_name VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    changed_by BIGINT,
    changed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tp_change_history_tp
    ON third_party_change_history (third_party_id, changed_at DESC);

-- 3. Roles con vigencia temporal (TER-04)
-- ============================================================================
CREATE TABLE IF NOT EXISTS third_party_role_assignments_v2 (
    id BIGSERIAL PRIMARY KEY,
    third_party_id BIGINT NOT NULL REFERENCES third_parties(id),
    role_id BIGINT NOT NULL REFERENCES third_party_role_catalog(id),
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tp_role_v2_active
    ON third_party_role_assignments_v2 (third_party_id, role_id)
    WHERE deleted_at IS NULL AND valid_to IS NULL;

-- Migrar datos existentes de la tabla join original
INSERT INTO third_party_role_assignments_v2 (third_party_id, role_id, valid_from, created_at)
SELECT tp.third_party_id, tp.role_id, CURRENT_DATE, NOW()
FROM third_party_role_assignments tp
WHERE NOT EXISTS (
    SELECT 1 FROM third_party_role_assignments_v2 v2
    WHERE v2.third_party_id = tp.third_party_id AND v2.role_id = tp.role_id AND v2.deleted_at IS NULL
);

-- 4. CommercialData: moneda y vigencia (TER-11/TER-12)
-- ============================================================================
ALTER TABLE commercial_data ADD COLUMN IF NOT EXISTS currency_id BIGINT REFERENCES cfg_currency_types(id);
ALTER TABLE commercial_data ADD COLUMN IF NOT EXISTS validity_from DATE;
ALTER TABLE commercial_data ADD COLUMN IF NOT EXISTS validity_to DATE;

-- 5. Historial de datos comerciales (TER-12)
-- ============================================================================
CREATE TABLE IF NOT EXISTS commercial_data_history (
    id BIGSERIAL PRIMARY KEY,
    commercial_data_id BIGINT NOT NULL REFERENCES commercial_data(id),
    field_name VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    changed_by BIGINT,
    changed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cd_history
    ON commercial_data_history (commercial_data_id, changed_at DESC);

-- 6. Cuentas bancarias de terceros (TER-05)
-- ============================================================================
CREATE TABLE IF NOT EXISTS third_party_bank_accounts (
    id BIGSERIAL PRIMARY KEY,
    third_party_id BIGINT NOT NULL REFERENCES third_parties(id),
    bank_account_id BIGINT NOT NULL REFERENCES bank_accounts(id),
    is_primary BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tp_bank_account_active
    ON third_party_bank_accounts (third_party_id, bank_account_id)
    WHERE deleted_at IS NULL;

-- 7. Menu de Datos Comerciales en modulo Terceros
-- ============================================================================
DO $$
DECLARE
    v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules WHERE name = 'Terceros' AND deleted_at IS NULL LIMIT 1;
    IF v_module_id IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Datos Comerciales', 'ri-money-dollar-circle-line', 'commercial-data', 3, v_module_id, 'ACTIVE', 'COMMERCIAL_DATA', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'COMMERCIAL_DATA' AND deleted_at IS NULL);
    END IF;
END $$;
