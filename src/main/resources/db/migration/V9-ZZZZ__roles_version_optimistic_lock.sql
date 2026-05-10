-- HU-PA-05 E4 (Bloque PA Bug 9, 2026-05-09): @Version optimistic locking en Role.
-- Hibernate ddl-auto NO altera nullability de columnas existentes y NO agrega
-- DEFAULT, asi que la columna debe crearse con DEFAULT 0 y backfill.
ALTER TABLE roles ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
UPDATE roles SET version = COALESCE(version, 0);
