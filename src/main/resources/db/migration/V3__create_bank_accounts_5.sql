-- Tabla cuentas bancarias (BNK-RF-01 a BNK-RF-05)
CREATE TABLE IF NOT EXISTS bank_accounts (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    account_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    bank_id BIGINT NOT NULL,
    currency_type_id BIGINT NOT NULL,
    initial_balance NUMERIC(15, 2) NOT NULL DEFAULT 0,
    chart_of_account_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    bank_branch_id BIGINT NULL,
    branch_name VARCHAR(100) NULL,
    account_executive VARCHAR(100) NULL,
    bank_phone VARCHAR(20) NULL,
    description VARCHAR(500) NULL,
    opening_date DATE NULL,
    allows_overdraft BOOLEAN NOT NULL DEFAULT FALSE,
    credit_limit NUMERIC(15, 2) NULL,
    notify_low_balance BOOLEAN NOT NULL DEFAULT FALSE,
    minimum_balance NUMERIC(15, 2) NULL,
    handles_checkbook BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    cost_center_id BIGINT NULL,
    -- TODO: Integrar con endpoint de busqueda de libros cuando este disponible (modulo externo)
    book_id BIGINT NULL,
    closing_date DATE NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bank_accounts_bank') THEN
        ALTER TABLE bank_accounts ADD CONSTRAINT fk_bank_accounts_bank FOREIGN KEY (bank_id) REFERENCES banks(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bank_accounts_currency') THEN
        ALTER TABLE bank_accounts ADD CONSTRAINT fk_bank_accounts_currency FOREIGN KEY (currency_type_id) REFERENCES cfg_currency_types(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bank_accounts_account') THEN
        ALTER TABLE bank_accounts ADD CONSTRAINT fk_bank_accounts_account FOREIGN KEY (accounting_account_id) REFERENCES accounting_accounts(id);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'bank_accounts' AND column_name = 'company_id') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bank_accounts_company') THEN
            ALTER TABLE bank_accounts ADD CONSTRAINT fk_bank_accounts_company FOREIGN KEY (company_id) REFERENCES companies(id);
        END IF;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bank_accounts_bank_branch') THEN
        ALTER TABLE bank_accounts ADD CONSTRAINT fk_bank_accounts_bank_branch FOREIGN KEY (bank_branch_id) REFERENCES bank_branches(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bank_accounts_cost_center') THEN
        ALTER TABLE bank_accounts ADD CONSTRAINT fk_bank_accounts_cost_center FOREIGN KEY (cost_center_id) REFERENCES cost_centers(id);
    END IF;
END $$;

DROP INDEX IF EXISTS uk_bank_accounts_number_bank_active;

DROP INDEX IF EXISTS uk_bank_accounts_code_company_active;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'bank_accounts' AND column_name = 'company_id') THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_bank_accounts_code_company_active
            ON bank_accounts (company_id, code) WHERE deleted_at IS NULL;
        CREATE UNIQUE INDEX IF NOT EXISTS uk_bank_accounts_number_bank_active
            ON bank_accounts (bank_id, account_number, company_id) WHERE deleted_at IS NULL;
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uk_bank_accounts_code_active
            ON bank_accounts (code) WHERE deleted_at IS NULL;
        CREATE UNIQUE INDEX IF NOT EXISTS uk_bank_accounts_number_bank_active_v2
            ON bank_accounts (bank_id, account_number) WHERE deleted_at IS NULL;
    END IF;
END $$;
