-- V13: Corregir index de NIT para que sea compuesto NIT+DV (PA-RF41-EF2).
-- El index anterior solo validaba NIT, pero la combinacion NIT+DV debe ser unica.
--
-- IDEMPOTENCIA (2026-04-14): Tras Fase 3, la tabla 'companies' fue eliminada
-- (Company deprecado, reemplazado por parametros de sistema). Este script se
-- conserva por historicidad pero se hace idempotente: solo actua si la tabla
-- 'companies' existe. Evita romper el arranque en BDs post-Fase 3.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'companies'
          AND table_schema = 'public'
    ) THEN
        DROP INDEX IF EXISTS uk_companies_nit_active;

        CREATE UNIQUE INDEX IF NOT EXISTS uk_companies_nit_dv_active
            ON companies (nit, dv)
            WHERE deleted_at IS NULL;
    END IF;
END $$;
