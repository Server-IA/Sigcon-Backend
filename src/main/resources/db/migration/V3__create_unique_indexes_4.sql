CREATE UNIQUE INDEX IF NOT EXISTS uk_assets_code_active
ON assets (asset_code)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_assets_taxes_retention_asset_id_tax_rule_id
ON assets_taxes_retention (asset_id, tax_rule_id)
WHERE deleted_at IS NULL;

DROP INDEX IF EXISTS uk_assets_taxes_retention_asset_id_accounting_account_id;
DROP INDEX IF EXISTS uk_assets_taxes_retention_tax_rule_id_accounting_account_id;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_assets_fields_not_null'
    ) THEN
        ALTER TABLE assets_taxes_retention
        ADD CONSTRAINT chk_assets_fields_not_null
        CHECK (percentage IS NOT NULL OR amount IS NOT NULL);
    END IF;
END
$$;