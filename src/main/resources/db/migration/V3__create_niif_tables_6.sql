-- ============================================
-- NIIF MODULE
-- ============================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- =========================
-- TABLE: niif_verifications
-- =========================
CREATE TABLE IF NOT EXISTS niif_verifications (
    id BIGSERIAL PRIMARY KEY,

    asset_id BIGINT NOT NULL,

    result VARCHAR(50) NOT NULL,

    summary VARCHAR(500),

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP NULL,

    CONSTRAINT fk_niif_verification_asset
        FOREIGN KEY (asset_id)
        REFERENCES assets(id)
);

-- =====================
-- TABLE: niif_alerts
-- =====================
CREATE TABLE IF NOT EXISTS niif_alerts (
    id BIGSERIAL PRIMARY KEY,

    verification_id BIGINT NOT NULL,

    severity VARCHAR(50) NOT NULL,

    message VARCHAR(500) NOT NULL,

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP NULL,

    CONSTRAINT fk_niif_alert_verification
        FOREIGN KEY (verification_id)
        REFERENCES niif_verifications(id)
        ON DELETE CASCADE
);

-- =====================
-- TABLE: niif_corrections
-- =====================
CREATE TABLE IF NOT EXISTS niif_corrections (
    id BIGSERIAL PRIMARY KEY,

    asset_id BIGINT NOT NULL,

    correction_type VARCHAR(50) NOT NULL,

    new_useful_life_months INTEGER,

    new_book_value NUMERIC(18,2),

    observations VARCHAR(500),

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP NULL,

    CONSTRAINT fk_niif_correction_asset
        FOREIGN KEY (asset_id)
        REFERENCES assets(id)
);

-- =====================
-- INDEXES
-- =====================
CREATE INDEX IF NOT EXISTS idx_niif_verification_asset
    ON niif_verifications(asset_id);

CREATE INDEX IF NOT EXISTS idx_niif_alert_verification
    ON niif_alerts(verification_id);

CREATE INDEX IF NOT EXISTS idx_niif_correction_asset
    ON niif_corrections(asset_id);

-- ============================================
-- CONSTRAINTS AVANZADAS (CONTROL DE NEGOCIO)
-- ============================================

-- Evitar múltiples verificaciones simultáneas activas por activo
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'unique_active_verification_per_asset'
    ) THEN
        ALTER TABLE niif_verifications
        ADD CONSTRAINT unique_active_verification_per_asset
        UNIQUE (asset_id, deleted_at)
        DEFERRABLE INITIALLY IMMEDIATE;
    END IF;
END;
$$;

-- ============================================
-- FUNCTIONS (VALIDACIONES)
-- ============================================

-- Validar que no se haga corrección a activos eliminados
CREATE OR REPLACE FUNCTION validate_asset_not_deleted()
RETURNS TRIGGER AS $$
DECLARE
    is_deleted BOOLEAN;
BEGIN

    SELECT (deleted_at IS NOT NULL)
    INTO is_deleted
    FROM assets
    WHERE id = NEW.asset_id;

    IF is_deleted THEN
        RAISE EXCEPTION
            USING
                MESSAGE = 'No se pueden aplicar correcciones a un activo eliminado',
                ERRCODE = '45000';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- TRIGGERS
-- ============================================

DROP TRIGGER IF EXISTS trg_validate_asset_not_deleted ON niif_corrections;

CREATE TRIGGER trg_validate_asset_not_deleted
BEFORE INSERT ON niif_corrections
FOR EACH ROW
EXECUTE FUNCTION validate_asset_not_deleted();

-- ============================================
-- TRIGGER: Auto actualizar updated_at
-- ============================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
   NEW.updated_at = NOW();
   RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_update_niif_verifications_updated_at ON niif_verifications;
CREATE TRIGGER trg_update_niif_verifications_updated_at
BEFORE UPDATE ON niif_verifications
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_update_niif_alerts_updated_at ON niif_alerts;
CREATE TRIGGER trg_update_niif_alerts_updated_at
BEFORE UPDATE ON niif_alerts
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_update_niif_corrections_updated_at ON niif_corrections;
CREATE TRIGGER trg_update_niif_corrections_updated_at
BEFORE UPDATE ON niif_corrections
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();