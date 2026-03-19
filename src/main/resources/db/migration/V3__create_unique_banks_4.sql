-- Reglas Bancos

CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_code_active
ON banks (code)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_name_active
ON banks (name)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_short_name_active
ON banks (name_short)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_nit_active
ON banks (nit)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_swift_active
ON banks (swift)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_banks_ach_active
ON banks (code_ach)
WHERE deleted_at IS NULL;




