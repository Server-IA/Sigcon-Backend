CREATE TABLE IF NOT EXISTS checkbooks (
    id BIGSERIAL PRIMARY KEY,
    bank_account_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,

    checkbook_number VARCHAR(30) NOT NULL,
    issuing_bank VARCHAR(150) NOT NULL,

    check_start_number BIGINT NOT NULL,
    check_end_number BIGINT NOT NULL,

    total_checks INTEGER NOT NULL,
    used_checks INTEGER NOT NULL DEFAULT 0,
    available_checks INTEGER NOT NULL,

    received_date DATE NOT NULL,
    activation_date DATE NULL,

    status VARCHAR(20) NOT NULL,
    observations VARCHAR(500) NULL,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_checkbooks_bank_account'
    ) THEN
        ALTER TABLE checkbooks
        ADD CONSTRAINT fk_checkbooks_bank_account
        FOREIGN KEY (bank_account_id)
        REFERENCES bank_accounts(id);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_checkbook_account_number_active
ON checkbooks (bank_account_id, checkbook_number)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_checkbooks_account
ON checkbooks (bank_account_id)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_checkbooks_status
ON checkbooks (status)
WHERE deleted_at IS NULL;