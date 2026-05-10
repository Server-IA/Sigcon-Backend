-- V0-A (2026-05-10) - HOTFIX preventivo CRITICO.
--
-- Contexto:
--   Cuando Hibernate ddl-auto=update agrega una columna nueva (ej. roles.version
--   para @Version optimistic lock) la crea como NOT NULL pero SIN DEFAULT a
--   nivel BD. Las migraciones legacy del DataInitializer (V9-J, V14, etc.) hacen
--   INSERT sin esa columna, asumiendo que tiene DEFAULT. Resultado: arranque
--   falla con "null value in column violates not-null constraint" y el backend
--   no levanta.
--
-- Esta migracion va con prefijo V0- para correr ANTES de TODO lo demas en el
-- orden alfabetico del DataInitializer (V0 < V1 < V2 < ... < V9 < V10).
--
-- Comportamiento:
--   - Defensiva: solo aplica ALTER si la columna existe (no rompe en BDs frescas
--     donde Hibernate aun no las creo - aunque normalmente Hibernate corre
--     ANTES que DataInitializer asi que las columnas ya estan).
--   - Idempotente: re-ejecutar no tiene efecto colateral.
--   - Backfill defensivo: UPDATE solo afecta filas con NULL.
--
-- Si en el futuro otra entidad agrega @Version u otro NOT NULL sin default,
-- agregar bloque DO aqui.

DO $$
BEGIN
  -- HU-PA-05 E4: Role.version (V9-ZZZZ + push 2026-05-10)
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_name = 'roles' AND column_name = 'version') THEN
    EXECUTE 'ALTER TABLE roles ALTER COLUMN version SET DEFAULT 0';
    EXECUTE 'UPDATE roles SET version = 0 WHERE version IS NULL';
  END IF;

  -- HU-AP-02 E3: Invoices.version (Bloque AT 2026-04-27, V9-ZZJ)
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_name = 'invoices' AND column_name = 'version') THEN
    EXECUTE 'ALTER TABLE invoices ALTER COLUMN version SET DEFAULT 0';
    EXECUTE 'UPDATE invoices SET version = 0 WHERE version IS NULL';
  END IF;
END $$;
