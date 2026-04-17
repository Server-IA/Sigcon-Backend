-- ============================================================================
-- V21: Mejoras al modulo de Activos Fijos
-- Cubre HUs: ACT-01, ACT-03, ACT-09, ACT-12
-- Fecha: 2026-04-13
-- ============================================================================

-- 1. Agregar campos de forma de pago a tabla assets (ACT-01/ACT-09)
-- ============================================================================
ALTER TABLE assets ADD COLUMN IF NOT EXISTS payment_form_id BIGINT NULL;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS payment_method_id BIGINT NULL;

-- 2. Tabla de bajas y transferencias de activos (ACT-03)
-- ============================================================================
CREATE TABLE IF NOT EXISTS asset_disposals (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL REFERENCES assets(id),
    disposal_type VARCHAR(20) NOT NULL,
    disposal_date DATE NOT NULL,
    disposal_amount NUMERIC(19,2),
    book_value_at_disposal NUMERIC(19,2) NOT NULL,
    gain_loss NUMERIC(19,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    destination_info VARCHAR(500),
    journal_entry_id BIGINT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_asset_disposals_asset ON asset_disposals (asset_id);
CREATE INDEX IF NOT EXISTS idx_asset_disposals_type ON asset_disposals (disposal_type);

-- 3. Tabla de revisiones anuales NIIF (ACT-12)
-- ============================================================================
CREATE TABLE IF NOT EXISTS asset_annual_reviews (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL REFERENCES assets(id),
    review_date DATE NOT NULL,
    fiscal_year INTEGER NOT NULL,
    previous_useful_life INTEGER,
    new_useful_life INTEGER,
    previous_residual_value NUMERIC(19,2),
    new_residual_value NUMERIC(19,2),
    previous_depreciation_monthly NUMERIC(19,2),
    new_depreciation_monthly NUMERIC(19,2),
    review_type VARCHAR(30) NOT NULL,
    justification VARCHAR(500),
    reviewed_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_asset_annual_reviews_asset ON asset_annual_reviews (asset_id);
CREATE INDEX IF NOT EXISTS idx_asset_annual_reviews_fiscal ON asset_annual_reviews (fiscal_year);
