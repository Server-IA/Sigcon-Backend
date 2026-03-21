-- Renombrar columna type_regime_id a type_regimen_id en la tabla companies
-- y actualizar la foreign key para que apunte a type_regimen en lugar de types_regimes

DO $$
DECLARE
    constraint_name_var TEXT;
BEGIN
    -- Buscar y eliminar la foreign key existente si existe
    SELECT constraint_name INTO constraint_name_var
    FROM information_schema.table_constraints
    WHERE table_name = 'companies'
      AND constraint_type = 'FOREIGN KEY'
      AND constraint_name LIKE '%type_regime%'
    LIMIT 1;

    IF constraint_name_var IS NOT NULL THEN
        EXECUTE format('ALTER TABLE companies DROP CONSTRAINT IF EXISTS %I', constraint_name_var);
    END IF;

    -- Si existe la tabla types_regimes y hay datos, migrarlos primero
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'types_regimes') THEN
        -- Migrar datos de types_regimes a type_regimen basándose en el código
        UPDATE companies c
        SET type_regime_id = (
            SELECT tr.id
            FROM type_regimen tr
            WHERE tr.code = (
                SELECT tr2.code
                FROM types_regimes tr2
                WHERE tr2.id = c.type_regime_id
            )
            LIMIT 1
        )
        WHERE EXISTS (
            SELECT 1
            FROM types_regimes tr
            WHERE tr.id = c.type_regime_id
        )
        AND c.type_regime_id IS NOT NULL;
    END IF;

    -- Renombrar la columna si existe
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'companies'
          AND column_name = 'type_regime_id'
    ) THEN
        ALTER TABLE companies
        RENAME COLUMN type_regime_id TO type_regimen_id;
    END IF;

    -- Crear la nueva foreign key apuntando a type_regimen
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_companies_type_regimen_id'
          AND table_name = 'companies'
    ) THEN
        ALTER TABLE companies
        ADD CONSTRAINT fk_companies_type_regimen_id
        FOREIGN KEY (type_regimen_id) REFERENCES type_regimen(id);
    END IF;

END $$;
