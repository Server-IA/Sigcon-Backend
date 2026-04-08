-- Reseteo
DROP INDEX IF EXISTS uk_assets_code_active;
DROP INDEX IF EXISTS uk_assets_name_active;

DROP INDEX IF EXISTS uk_risk_segmentation_client_active;

-- Activos
CREATE UNIQUE INDEX IF NOT EXISTS uk_assets_code_active
ON assets (company_id, asset_code)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_assets_name_active
ON assets (company_id, asset_name)
WHERE deleted_at IS NULL;

-- risk_segmentation
CREATE UNIQUE INDEX IF NOT EXISTS uk_risk_segmentation_client_active
ON risk_segmentation (client_id)
WHERE deleted_at IS NULL;