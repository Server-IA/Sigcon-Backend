-- V19: Motor central de asientos contables (JournalEntryService)
-- Tablas: journal_entries (cabecera) y journal_entry_lines (lineas de detalle)

CREATE TABLE IF NOT EXISTS journal_entries (
    id BIGSERIAL PRIMARY KEY,
    entry_number BIGINT NOT NULL,
    fiscal_year INTEGER NOT NULL,
    entry_date DATE NOT NULL,
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL,
    description VARCHAR(500),
    source_module VARCHAR(10) NOT NULL,
    source_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    reversal_of BIGINT REFERENCES journal_entries(id),
    correction_of BIGINT REFERENCES journal_entries(id),
    total_debit NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_credit NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP,
    CONSTRAINT uq_entry_number_year UNIQUE(entry_number, fiscal_year),
    CONSTRAINT chk_je_status CHECK (status IN ('DRAFT','POSTED','REVERSED')),
    CONSTRAINT chk_je_source CHECK (source_module IN ('AP','AR','BNK','ACT','NOM','CG'))
);

CREATE TABLE IF NOT EXISTS journal_entry_lines (
    id BIGSERIAL PRIMARY KEY,
    journal_entry_id BIGINT NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    line_order INTEGER NOT NULL,
    accounting_account_id BIGINT NOT NULL REFERENCES accounting_accounts(id),
    debit_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    credit_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    description VARCHAR(500),
    third_party_nit VARCHAR(20),
    cost_center_id BIGINT REFERENCES cost_centers(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_je_fiscal_year ON journal_entries(fiscal_year);
CREATE INDEX IF NOT EXISTS idx_je_status ON journal_entries(status);
CREATE INDEX IF NOT EXISTS idx_je_source ON journal_entries(source_module, source_id);
CREATE INDEX IF NOT EXISTS idx_je_period ON journal_entries(period_year, period_month);
CREATE INDEX IF NOT EXISTS idx_je_deleted ON journal_entries(deleted_at);
CREATE INDEX IF NOT EXISTS idx_jel_entry ON journal_entry_lines(journal_entry_id);
CREATE INDEX IF NOT EXISTS idx_jel_account ON journal_entry_lines(accounting_account_id);
