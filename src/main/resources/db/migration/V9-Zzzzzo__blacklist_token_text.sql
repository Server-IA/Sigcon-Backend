-- QA Auditoria (2026-06-02): el logout fallaba con HTTP 400 "Error de integridad
-- de datos" y por eso el evento LOGOUT NO se registraba en el log de auditoria.
--
-- Causa raiz: el JWT crecio (claims de permisos + sessionId + companyId) y supera
-- los 5000 chars de la columna blacklisted_tokens.token (varchar(5000)). Al hacer
-- logout, AuthService.logout intenta guardar el token en la blacklist y Postgres
-- rechaza el INSERT por longitud -> DataIntegrityViolation -> 400 -> el
-- auditUserEvent(LOGOUT) nunca se ejecuta.
--
-- Fix: ampliar token a TEXT. El UNIQUE btree no soporta valores >2704 bytes, asi
-- que se reemplaza por un indice HASH (soporta igualdad sobre valores largos, que
-- es exactamente lo que hace existsByToken). La unicidad funcional ya la garantiza
-- AuthService (chequea existsByToken antes de insertar).

-- 1) Quitar cualquier constraint UNIQUE existente sobre la columna token.
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'blacklisted_tokens'::regclass AND contype = 'u' LOOP
        EXECUTE 'ALTER TABLE blacklisted_tokens DROP CONSTRAINT ' || quote_ident(c.conname);
    END LOOP;
EXCEPTION WHEN undefined_table THEN
    NULL; -- la tabla aun no existe (primer arranque): Hibernate la creara con la entidad TEXT
END $$;

-- 2) Quitar indices unicos sueltos sobre token (por si quedaron de Hibernate).
DO $$
DECLARE i RECORD;
BEGIN
    FOR i IN SELECT indexrelid::regclass AS idxname
             FROM pg_index
             WHERE indrelid = 'blacklisted_tokens'::regclass AND indisunique = true
               AND indexrelid::regclass::text NOT LIKE '%pkey%' LOOP
        EXECUTE 'DROP INDEX IF EXISTS ' || i.idxname;
    END LOOP;
EXCEPTION WHEN undefined_table THEN
    NULL;
END $$;

-- 3) Ampliar la columna a TEXT (soporta JWT de cualquier tamano).
DO $$
BEGIN
    ALTER TABLE blacklisted_tokens ALTER COLUMN token TYPE TEXT;
EXCEPTION WHEN undefined_table THEN
    NULL;
END $$;

-- 4) Indice HASH para lookups por igualdad (existsByToken) sobre valores largos.
DO $$
BEGIN
    CREATE INDEX IF NOT EXISTS idx_blacklist_token_hash ON blacklisted_tokens USING hash (token);
EXCEPTION WHEN undefined_table THEN
    NULL;
END $$;
