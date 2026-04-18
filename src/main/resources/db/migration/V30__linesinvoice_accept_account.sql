-- V30: Permitir que las lineas de factura acepten cuenta contable + descripcion
-- como alternativa al activo fijo obligatorio. Mantiene retrocompatibilidad.

-- 1. Hacer asset_id nullable (antes NOT NULL)
ALTER TABLE lines_invoice ALTER COLUMN asset_id DROP NOT NULL;

-- 2. Agregar accounting_account_id como alternativa a asset_id
ALTER TABLE lines_invoice ADD COLUMN IF NOT EXISTS accounting_account_id BIGINT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_lines_invoice_accounting_account'
          AND table_name = 'lines_invoice'
    ) THEN
        ALTER TABLE lines_invoice
            ADD CONSTRAINT fk_lines_invoice_accounting_account
            FOREIGN KEY (accounting_account_id) REFERENCES accounting_accounts(id);
    END IF;
END $$;

-- 3. Agregar descripcion libre del item facturado
ALTER TABLE lines_invoice ADD COLUMN IF NOT EXISTS description VARCHAR(500);

-- 4. Check constraint: al menos una referencia (asset o cuenta contable) debe existir
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'ck_lines_invoice_source'
          AND table_name = 'lines_invoice'
    ) THEN
        ALTER TABLE lines_invoice
            ADD CONSTRAINT ck_lines_invoice_source
            CHECK (asset_id IS NOT NULL OR accounting_account_id IS NOT NULL);
    END IF;
END $$;
