-- Sesiones de conciliación bancaria y empareje con comprobantes (vouchers)
CREATE TABLE IF NOT EXISTS bank_reconciliation_sessions (
    id BIGSERIAL PRIMARY KEY,
    bank_account_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    statement_opening_balance NUMERIC(20, 2) NULL,
    statement_closing_balance NUMERIC(20, 2) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    notes VARCHAR(500) NULL,
    closed_at TIMESTAMP NULL,
    closed_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_brs_bank_account') THEN
        ALTER TABLE bank_reconciliation_sessions
            ADD CONSTRAINT fk_brs_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_brs_company') THEN
        ALTER TABLE bank_reconciliation_sessions
            ADD CONSTRAINT fk_brs_company FOREIGN KEY (company_id) REFERENCES companies (id);
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'users'
    ) THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_brs_closed_by') THEN
            ALTER TABLE bank_reconciliation_sessions
                ADD CONSTRAINT fk_brs_closed_by FOREIGN KEY (closed_by) REFERENCES users (id);
        END IF;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_brs_bank_account ON bank_reconciliation_sessions (bank_account_id, period_end DESC);

ALTER TABLE financial_movements ADD COLUMN IF NOT EXISTS reconciliation_session_id BIGINT NULL;
ALTER TABLE financial_movements ADD COLUMN IF NOT EXISTS matched_voucher_id BIGINT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_fin_mov_reconciliation_session') THEN
        ALTER TABLE financial_movements
            ADD CONSTRAINT fk_fin_mov_reconciliation_session
            FOREIGN KEY (reconciliation_session_id) REFERENCES bank_reconciliation_sessions (id) ON DELETE SET NULL;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'vouchers'
    ) THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_fin_mov_matched_voucher') THEN
            ALTER TABLE financial_movements
                ADD CONSTRAINT fk_fin_mov_matched_voucher
                FOREIGN KEY (matched_voucher_id) REFERENCES vouchers (id) ON DELETE SET NULL;
        END IF;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_fin_mov_session ON financial_movements (reconciliation_session_id);
