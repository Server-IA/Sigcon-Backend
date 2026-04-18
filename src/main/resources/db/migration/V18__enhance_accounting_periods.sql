-- V18: Mejorar tabla de periodos contables
-- Agregar estado LOCKED y campos de bloqueo

ALTER TABLE accounting_periods DROP CONSTRAINT IF EXISTS accounting_periods_status_check;
ALTER TABLE accounting_periods ADD CONSTRAINT accounting_periods_status_check
    CHECK (status IN ('OPEN', 'CLOSED', 'LOCKED'));

ALTER TABLE accounting_periods ADD COLUMN IF NOT EXISTS locked_at TIMESTAMP;
ALTER TABLE accounting_periods ADD COLUMN IF NOT EXISTS locked_by VARCHAR(255);
ALTER TABLE accounting_periods ADD COLUMN IF NOT EXISTS notes VARCHAR(500);
