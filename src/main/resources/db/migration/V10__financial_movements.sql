-- Movimientos financieros (banco / caja) para conciliación BNK-RF-24 y cierre de extracto
CREATE TABLE IF NOT EXISTS financial_movements (
    id BIGSERIAL PRIMARY KEY,
    bank_account_id BIGINT NULL,
    cash_id BIGINT NULL,
    company_id BIGINT NOT NULL,
    movement_date DATE NOT NULL,
    amount NUMERIC(20, 2) NOT NULL,
    description VARCHAR(500),
    external_reference VARCHAR(100),
    source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    matched_check_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_fin_mov_bank_xor_cash CHECK (
        (bank_account_id IS NOT NULL AND cash_id IS NULL)
        OR (bank_account_id IS NULL AND cash_id IS NOT NULL)
    )
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_fin_mov_bank_account') THEN
        ALTER TABLE financial_movements
            ADD CONSTRAINT fk_fin_mov_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts (id);
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'cash'
    ) THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_fin_mov_cash') THEN
            ALTER TABLE financial_movements
                ADD CONSTRAINT fk_fin_mov_cash FOREIGN KEY (cash_id) REFERENCES cash (id);
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'financial_movements' AND column_name = 'company_id') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_fin_mov_company') THEN
            ALTER TABLE financial_movements
                ADD CONSTRAINT fk_fin_mov_company FOREIGN KEY (company_id) REFERENCES companies (id);
        END IF;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_fin_mov_matched_check') THEN
        ALTER TABLE financial_movements
            ADD CONSTRAINT fk_fin_mov_matched_check FOREIGN KEY (matched_check_id) REFERENCES checks (id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_fin_mov_bank_account_date ON financial_movements (bank_account_id, movement_date DESC);
CREATE INDEX IF NOT EXISTS idx_fin_mov_cash ON financial_movements (cash_id);
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'financial_movements' AND column_name = 'company_id') THEN
        CREATE INDEX IF NOT EXISTS idx_fin_mov_company ON financial_movements (company_id);
    END IF;
END $$;

ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS last_reconciliation_date DATE NULL;
