-- =============================================================================
-- V9-Z2: registra menu "Dashboard" en el modulo Plataforma (HU-PA-PLAT-06).
--
-- Depende de V10-E que crea el modulo "Plataforma" con url="platform".
-- Este menu queda con component="PLATFORM_DASHBOARD" que el frontend mapea
-- a la pagina /platform/dashboard (envuelta en PlatformRoute gatekeeper).
-- =============================================================================
DO $$
DECLARE v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules
     WHERE name = 'Plataforma' AND deleted_at IS NULL LIMIT 1;

    IF v_module_id IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Dashboard', 'ri-dashboard-line', 'dashboard', 5, v_module_id, 'ACTIVE', 'PLATFORM_DASHBOARD', true, NOW(), NOW()
         WHERE NOT EXISTS (
             SELECT 1 FROM menus WHERE component = 'PLATFORM_DASHBOARD' AND deleted_at IS NULL
         );
    END IF;
END $$;
