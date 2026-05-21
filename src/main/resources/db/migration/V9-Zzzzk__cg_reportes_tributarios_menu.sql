-- QA Bloque BP (HU-CG-12, 2026-05-19): registrar el menu visual del nuevo
-- modulo "Reportes Tributarios" en CG. El backend ya expone los endpoints
-- /api/v1/cg/tax-reports (ECL, IVA, diferencias en cambio, resumen anual)
-- desde Bloques anteriores; aqui se hace visible al contador desde el
-- sidebar del modulo Contabilidad General.
--
-- Idempotente: el INSERT usa WHERE NOT EXISTS para no duplicar la fila si
-- se vuelve a ejecutar.

DO $$
DECLARE
    v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules
     WHERE LOWER(name) = LOWER('Contabilidad General')
       AND deleted_at IS NULL
     LIMIT 1;

    IF v_module_id IS NULL THEN
        RAISE NOTICE 'V9-Zzzzk: modulo Contabilidad General no existe, se omite seed.';
        RETURN;
    END IF;

    -- Verifica que el permiso exista. CG.REPORTES.VER ya esta sembrado
    -- por V9-J/V9-K (RBAC). Si no existe en algun ambiente, el menu se
    -- crea con required_permission_code NULL (todos lo verian — el
    -- contador podra pedirlo). Esto evita que la migracion explote.
    IF NOT EXISTS (
        SELECT 1 FROM menus
         WHERE component = 'CG_REPORTES_TRIBUTARIOS'
           AND module_id = v_module_id
           AND deleted_at IS NULL
    ) THEN
        INSERT INTO menus (
            label, icon, path, menu_order, module_id, status, component,
            visible, required_permission_code, created_at, updated_at
        )
        SELECT 'Reportes Tributarios', 'ri-pie-chart-line', 'reportes-tributarios',
               (SELECT COALESCE(MAX(menu_order), 0) + 1
                  FROM menus WHERE module_id = v_module_id),
               v_module_id, 'ACTIVE', 'CG_REPORTES_TRIBUTARIOS', true,
               CASE WHEN EXISTS (SELECT 1 FROM permissions
                                  WHERE code = 'CG.REPORTES.VER'
                                    AND deleted_at IS NULL)
                    THEN 'CG.REPORTES.VER'
                    ELSE NULL
                END,
               NOW(), NOW();
        RAISE NOTICE 'V9-Zzzzk: menu CG_REPORTES_TRIBUTARIOS creado.';
    ELSE
        RAISE NOTICE 'V9-Zzzzk: menu CG_REPORTES_TRIBUTARIOS ya existia, se preserva.';
    END IF;
END $$;
