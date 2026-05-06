-- HU-ACT-09 (QA 2026-05-05): se eliminan las HUs del modulo Cumplimiento NIIF
-- y el menu queda obsoleto. Este script soft-elimina el menu para que no
-- aparezca mas en el sidebar. Idempotente.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM menus
         WHERE component = 'NIIF_CORRECTION'
           AND deleted_at IS NULL
    ) THEN
        UPDATE menus
           SET deleted_at = NOW(), updated_at = NOW()
         WHERE component = 'NIIF_CORRECTION'
           AND deleted_at IS NULL;

        RAISE NOTICE 'Menu Cumplimiento NIIF (NIIF_CORRECTION) eliminado (HU-ACT-09 fuera de alcance).';
    END IF;
END $$;
