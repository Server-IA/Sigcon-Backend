-- V18: Mejorar tabla de periodos contables
-- Agregar estado LOCKED y campos de bloqueo
--
-- QA-BLOQUE-AN (2026-04-29): el CHECK constraint se crea solo si no existe,
-- evitando re-aplicar en cada arranque (DataInitializer corre en cada cold-start).
-- Antes: DROP IF EXISTS + ADD se ejecutaba siempre, machacando cualquier
-- modificacion manual hecha desde la UI/admin.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'accounting_periods_status_check'
           AND conrelid = 'accounting_periods'::regclass
    ) THEN
        ALTER TABLE accounting_periods ADD CONSTRAINT accounting_periods_status_check
            CHECK (status IN ('OPEN', 'CLOSED', 'LOCKED'));
    END IF;
END $$;

ALTER TABLE accounting_periods ADD COLUMN IF NOT EXISTS locked_at TIMESTAMP;
ALTER TABLE accounting_periods ADD COLUMN IF NOT EXISTS locked_by VARCHAR(255);
ALTER TABLE accounting_periods ADD COLUMN IF NOT EXISTS notes VARCHAR(500);
