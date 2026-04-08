-- Reseteo
DROP INDEX IF EXISTS uk_banks_code_active;
DROP INDEX IF EXISTS uk_banks_name_active;
DROP INDEX IF EXISTS uk_banks_short_name_active;
DROP INDEX IF EXISTS uk_banks_nit_active;
DROP INDEX IF EXISTS uk_banks_swift_active;
DROP INDEX IF EXISTS uk_banks_ach_active;

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


-- Chequeras

CREATE UNIQUE INDEX IF NOT EXISTS uk_checkbook_account_number_active
ON checkbooks (bank_account_id, checkbook_number)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_checkbooks_account
ON checkbooks (bank_account_id)
WHERE deleted_at IS NULL;


CREATE INDEX IF NOT EXISTS idx_checkbooks_company
ON checkbooks (company_id)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_checkbooks_status
ON checkbooks (status)
WHERE deleted_at IS NULL;