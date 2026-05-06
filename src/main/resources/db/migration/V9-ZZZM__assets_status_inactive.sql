-- QA-2026-05-05 (HU-ACT-03 E3): permitir el valor INACTIVE en el CHECK
-- constraint de assets.asset_status. Antes solo aceptaba ACTIVE/IN_REPAIR/
-- DECOMMISSIONED/TRANSFERRED y el PUT desde la UI fallaba con
-- "violates check constraint assets_asset_status_check" cuando el contador
-- editaba un activo y selecciona el nuevo estado "Inactivo".
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'assets_asset_status_check'
    ) THEN
        ALTER TABLE assets DROP CONSTRAINT assets_asset_status_check;
    END IF;
END $$;

ALTER TABLE assets
ADD CONSTRAINT assets_asset_status_check
CHECK (asset_status IN ('ACTIVE','INACTIVE','IN_REPAIR','DECOMMISSIONED','TRANSFERRED'));
