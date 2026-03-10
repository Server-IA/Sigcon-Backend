CREATE UNIQUE INDEX IF NOT EXISTS uk_assets_code_active
ON assets (asset_code)
WHERE deleted_at IS NULL;
