-- V9-O: dropear los UNIQUE globales que Hibernate creo por @Column(unique=true)
-- sobre permissions.name y permissions.code. Esos indices NO respetan soft-delete
-- (deleted_at != NULL) y provocaban colisiones en los re-seeds cada vez que Dokploy
-- redesplegaba (DataInitializer re-ejecuta TODOS los scripts en cada arranque).
--
-- Estrategia:
-- 1) Identificar dinamicamente los UNIQUE CONSTRAINTS sobre permissions.name o
--    permissions.code cuyo nombre NO empieza con 'uk_permissions_' (prefijo nuestro),
--    o sea los generados por Hibernate con hash (ukXXXXXXXX).
-- 2) Dropearlos. Mantener los indices propios (uk_permissions_active sobre code,
--    y el que creamos abajo sobre name).
-- 3) Crear UNIQUE parcial sobre name que respete soft-delete.
--
-- Idempotente: se puede ejecutar varias veces sin error.
-- No destructivo: no borra filas, solo cambia reglas de unicidad.

DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT DISTINCT tc.constraint_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON tc.constraint_name = kcu.constraint_name
         AND tc.table_schema = kcu.table_schema
        WHERE tc.table_name = 'permissions'
          AND tc.constraint_type = 'UNIQUE'
          AND kcu.column_name IN ('name', 'code')
          AND tc.constraint_name NOT LIKE 'uk_permissions_%'
    LOOP
        EXECUTE 'ALTER TABLE permissions DROP CONSTRAINT IF EXISTS "'
            || r.constraint_name || '" CASCADE';
        RAISE NOTICE 'Dropped constraint: %', r.constraint_name;
    END LOOP;
END $$;

-- UNIQUE parcial sobre name (solo filas activas). Mismo patron que el que ya
-- existe sobre code (uk_permissions_active).
CREATE UNIQUE INDEX IF NOT EXISTS uk_permissions_name_active
    ON permissions (name) WHERE deleted_at IS NULL;
