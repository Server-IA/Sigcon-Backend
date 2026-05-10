-- HU-PA-07 E3 (QA Bloque PA Bug 15, 2026-05-09): agregar estado BLOCKED al
-- enum Status de users. Antes el CHECK solo aceptaba ACTIVE/INACTIVE; agregar
-- BLOCKED para permitir bloquear usuarios temporalmente sin desactivarlos.
DO $$
BEGIN
    -- Buscar y eliminar el CHECK constraint viejo si existe
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'users_status_check' AND conrelid = 'users'::regclass
    ) THEN
        EXECUTE 'ALTER TABLE users DROP CONSTRAINT users_status_check';
    END IF;
END $$;

-- Recrear el CHECK con los 3 valores aceptados
ALTER TABLE users ADD CONSTRAINT users_status_check
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'));
