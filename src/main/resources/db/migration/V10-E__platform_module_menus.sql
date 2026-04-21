-- =============================================================================
-- V10-E: Modulo "Plataforma" + menus para PLATFORM_ADMIN (Bloque F)
-- Fecha: 2026-04-19
-- HU-PLAT-01 / 02 / 05: gestion de empresas cross-tenant.
-- =============================================================================

-- 1. Modulo "Plataforma" (id dinamico). Idempotente via name unique.
INSERT INTO modules (name, description, icon, url, position, status, created_at, updated_at)
SELECT 'Plataforma', 'Administracion cross-empresa (solo PLATFORM_ADMIN)',
       'ri-building-4-line', 'platform', 100, 'ACTIVE', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM modules WHERE name = 'Plataforma' AND deleted_at IS NULL);

-- 2. Menu "Empresas" bajo Plataforma.
DO $$
DECLARE v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules
     WHERE name = 'Plataforma' AND deleted_at IS NULL LIMIT 1;
    IF v_module_id IS NULL THEN RETURN; END IF;

    INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
    SELECT 'Empresas', 'ri-building-line', 'empresas', 10, v_module_id, 'ACTIVE',
           'PLATFORM_EMPRESAS', true, NOW(), NOW()
     WHERE NOT EXISTS (
         SELECT 1 FROM menus WHERE component = 'PLATFORM_EMPRESAS' AND deleted_at IS NULL
     );
END $$;
