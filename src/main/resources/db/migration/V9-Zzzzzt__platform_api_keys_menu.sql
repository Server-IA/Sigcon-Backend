-- =============================================================================
-- PA-RF-28 (Pendientes PA, 2026-06-03)
-- Menu "API Keys" bajo el modulo Plataforma para el ciclo de vida de las
-- credenciales AAEF (generar / listar / revocar). Idempotente (component unico).
-- Solo lo ve PLATFORM_ADMIN (la pagina va envuelta con PlatformRoute y los
-- endpoints exigen @PreAuthorize('PLATFORM_ADMIN')).
-- =============================================================================
DO $$
DECLARE v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules
     WHERE name = 'Plataforma' AND deleted_at IS NULL LIMIT 1;
    IF v_module_id IS NULL THEN RETURN; END IF;

    INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
    SELECT 'API Keys', 'ri-key-2-line', 'api-keys', 30, v_module_id, 'ACTIVE',
           'PLATFORM_API_KEYS', true, NOW(), NOW()
     WHERE NOT EXISTS (
         SELECT 1 FROM menus WHERE component = 'PLATFORM_API_KEYS' AND deleted_at IS NULL
     );
    RAISE NOTICE 'V9-Zzzzzt: menu PLATFORM_API_KEYS creado bajo Plataforma';
END $$;
