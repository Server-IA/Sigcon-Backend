CREATE TABLE IF NOT EXISTS checkbooks (
    id BIGSERIAL PRIMARY KEY,
    bank_account_id BIGINT NOT NULL,
    checkbook_number VARCHAR(30) NOT NULL,
    issuing_bank VARCHAR(150) NOT NULL,
    check_start_number BIGINT NOT NULL,
    check_end_number BIGINT NOT NULL,
    total_checks INTEGER NOT NULL,
    used_checks INTEGER NOT NULL DEFAULT 0,
    available_checks INTEGER NOT NULL,
    received_date DATE NOT NULL,
    activation_date DATE,
    status VARCHAR(20) NOT NULL,
    observations VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP NULL
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_checkbook'
    ) THEN
        ALTER TABLE checkbooks
        ADD CONSTRAINT uk_checkbook UNIQUE (bank_account_id, checkbook_number);
    END IF;
END $$;
