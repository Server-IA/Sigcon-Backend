-- HU-CFG-RF-05/07 (Bloque AP, 2026-05-04): asociar opcionalmente una regla de
-- depreciacion a cada cuenta contable. Permite que el listado y el formulario
-- de edicion expongan el campo "Regla de Depreciacion" como opcional.
--
-- Idempotente: solo agrega la columna si no existe. Hibernate ddl-auto la
-- creara automaticamente al arrancar gracias al campo en la entidad, pero
-- esta migracion es defensiva para BDs que ya esten provisionadas con
-- ddl-auto=validate o que se restauren desde backup.

ALTER TABLE accounting_accounts
    ADD COLUMN IF NOT EXISTS depretation_rule_id BIGINT NULL;

-- Indice para acelerar lookups por regla (futuro: reportes de cuentas con
-- regla de depreciacion asignada).
CREATE INDEX IF NOT EXISTS idx_accounting_accounts_depretation_rule_id
    ON accounting_accounts (depretation_rule_id)
    WHERE depretation_rule_id IS NOT NULL;
