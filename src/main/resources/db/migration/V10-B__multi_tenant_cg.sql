-- =============================================================================
-- V10-B: Multi-tenant para modulo Contabilidad General (CG) + cuentas + CC
-- Fecha: 2026-04-19
-- Bloque C - Dia 8: propaga company_id + FK a Company en 5 entidades CG-core.
--
-- Entidades tenant-scoped:
--   - accounting_accounts   (cuentas operativas sobre PUC)
--   - accounting_periods    (periodos contables por empresa)
--   - cost_centers          (centros de costo)
--   - journal_entries       (asientos contables)
--   - journal_entry_lines   (lineas de asiento - denormalizado para queries directos)
--
-- Estrategia:
--   1. ALTER TABLE ... ADD COLUMN company_id BIGINT NULL (idempotente via IF NOT EXISTS).
--   2. Backfill a la empresa default (id=1 "SIGCON DEMO").
--   3. SET NOT NULL + FK a companies(id).
--   4. Reemplazar UNIQUE(year,month) por UNIQUE(company_id, year, month) en accounting_periods.
--   5. Indices company_id para queries con @Filter.
-- =============================================================================

-- 1) accounting_accounts
ALTER TABLE accounting_accounts ADD COLUMN IF NOT EXISTS company_id BIGINT NULL;
UPDATE accounting_accounts SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE accounting_accounts ALTER COLUMN company_id SET NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                    WHERE constraint_name = 'fk_accounting_accounts_company'
                      AND table_name = 'accounting_accounts') THEN
        ALTER TABLE accounting_accounts
            ADD CONSTRAINT fk_accounting_accounts_company
            FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_accounting_accounts_company ON accounting_accounts(company_id);

-- 2) accounting_periods (cambia el UNIQUE para incluir company_id)
ALTER TABLE accounting_periods ADD COLUMN IF NOT EXISTS company_id BIGINT NULL;
UPDATE accounting_periods SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE accounting_periods ALTER COLUMN company_id SET NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                    WHERE constraint_name = 'fk_accounting_periods_company'
                      AND table_name = 'accounting_periods') THEN
        ALTER TABLE accounting_periods
            ADD CONSTRAINT fk_accounting_periods_company
            FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END $$;
-- Drop old unique (year, month) si existe con nombre canonico o con hash de Hibernate
DO $$
DECLARE c_name text;
BEGIN
    FOR c_name IN
        SELECT conname FROM pg_constraint
         WHERE conrelid = 'accounting_periods'::regclass
           AND contype = 'u'
           AND conname IN ('uk_accounting_periods_year_month')
    LOOP
        EXECUTE 'ALTER TABLE accounting_periods DROP CONSTRAINT ' || quote_ident(c_name);
    END LOOP;
END $$;
DROP INDEX IF EXISTS uk_accounting_periods_year_month;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conname = 'uk_accounting_periods_company_year_month'
                      AND conrelid = 'accounting_periods'::regclass) THEN
        ALTER TABLE accounting_periods
            ADD CONSTRAINT uk_accounting_periods_company_year_month
            UNIQUE (company_id, year, month);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_accounting_periods_company ON accounting_periods(company_id);

-- 3) cost_centers
ALTER TABLE cost_centers ADD COLUMN IF NOT EXISTS company_id BIGINT NULL;
UPDATE cost_centers SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE cost_centers ALTER COLUMN company_id SET NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                    WHERE constraint_name = 'fk_cost_centers_company'
                      AND table_name = 'cost_centers') THEN
        ALTER TABLE cost_centers
            ADD CONSTRAINT fk_cost_centers_company
            FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_cost_centers_company ON cost_centers(company_id);

-- 4) journal_entries
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS company_id BIGINT NULL;
UPDATE journal_entries SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE journal_entries ALTER COLUMN company_id SET NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                    WHERE constraint_name = 'fk_journal_entries_company'
                      AND table_name = 'journal_entries') THEN
        ALTER TABLE journal_entries
            ADD CONSTRAINT fk_journal_entries_company
            FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_journal_entries_company ON journal_entries(company_id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_company_fy ON journal_entries(company_id, fiscal_year, entry_number);

-- 5) journal_entry_lines (denormalizado para queries de saldo directos)
ALTER TABLE journal_entry_lines ADD COLUMN IF NOT EXISTS company_id BIGINT NULL;
UPDATE journal_entry_lines l SET company_id = je.company_id
  FROM journal_entries je WHERE l.journal_entry_id = je.id AND l.company_id IS NULL;
ALTER TABLE journal_entry_lines ALTER COLUMN company_id SET NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                    WHERE constraint_name = 'fk_journal_entry_lines_company'
                      AND table_name = 'journal_entry_lines') THEN
        ALTER TABLE journal_entry_lines
            ADD CONSTRAINT fk_journal_entry_lines_company
            FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_journal_entry_lines_company ON journal_entry_lines(company_id);
CREATE INDEX IF NOT EXISTS idx_journal_entry_lines_company_account
    ON journal_entry_lines(company_id, accounting_account_id);
