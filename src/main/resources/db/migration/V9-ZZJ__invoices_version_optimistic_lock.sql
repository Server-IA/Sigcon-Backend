-- =============================================================================
-- V9-ZZJ__invoices_version_optimistic_lock.sql
--
-- HU-AP-02 E3: agrega columna `version` a invoices (Hibernate @Version) para
-- optimistic locking. Antes del fix dos usuarios editando la misma factura
-- simultaneamente generaban "Could not commit JPA transaction" (error generico).
-- Con @Version, el segundo usuario recibe HTTP 409 con mensaje legible:
--   "Esta factura fue modificada por otro usuario. Recarga los datos y vuelve
--    a intentarlo."
--
-- La columna se hace NOT NULL pero con DEFAULT 0 para no romper filas
-- existentes. Hibernate ddl-auto=update no podia agregar NOT NULL solo (al no
-- tener defaultValue de la entidad), por eso usamos esta migracion.
-- Es idempotente: si ya existe, solo backfilla nulls y aplica NOT NULL.
-- =============================================================================

DO $$
BEGIN
    -- 1. Crear columna si no existe.
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='invoices' AND column_name='version'
    ) THEN
        ALTER TABLE invoices ADD COLUMN version BIGINT DEFAULT 0;
        RAISE NOTICE 'V9-ZZJ: columna version agregada a invoices.';
    END IF;

    -- 2. Backfill de filas con version IS NULL (defensivo).
    UPDATE invoices SET version = 0 WHERE version IS NULL;

    -- 3. Forzar NOT NULL (idempotente).
    ALTER TABLE invoices ALTER COLUMN version SET NOT NULL;
    ALTER TABLE invoices ALTER COLUMN version SET DEFAULT 0;
END $$;
