-- =============================================================================
-- PA-RF-PLAT-07 v3.0 (Control de Cambios PA, 2026-05-29)
-- Menu "Administradores" bajo el modulo Plataforma para el ciclo de vida de
-- los PLATFORM_ADMIN. Idempotente (component unico). Solo lo ve PLATFORM_ADMIN.
-- =============================================================================
DO $$
DECLARE v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules
     WHERE name = 'Plataforma' AND deleted_at IS NULL LIMIT 1;
    IF v_module_id IS NULL THEN RETURN; END IF;

    INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
    SELECT 'Administradores', 'ri-shield-user-line', 'administradores', 20, v_module_id, 'ACTIVE',
           'PLATFORM_ADMINS', true, NOW(), NOW()
     WHERE NOT EXISTS (
         SELECT 1 FROM menus WHERE component = 'PLATFORM_ADMINS' AND deleted_at IS NULL
     );
    RAISE NOTICE 'V9-Zzzzzk: menu PLATFORM_ADMINS creado bajo Plataforma';
END $$;
