-- Sprint 2 — registro de menus para Identidad Visual (BRAND-01) y Navegacion (NAV-01)
-- en el modulo Parametrizacion. Idempotente.
DO $$
DECLARE v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules WHERE name = 'Parametrizacion' AND deleted_at IS NULL LIMIT 1;
    IF v_module_id IS NULL THEN
        SELECT id INTO v_module_id FROM modules WHERE name ILIKE 'parametr%' AND deleted_at IS NULL LIMIT 1;
    END IF;

    IF v_module_id IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Identidad Visual', 'ri-palette-line', 'identidad-visual', 90, v_module_id, 'ACTIVE', 'IDENTIDAD_VISUAL', true, NOW(), NOW()
         WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'IDENTIDAD_VISUAL' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Navegacion', 'ri-menu-line', 'navegacion', 91, v_module_id, 'ACTIVE', 'NAVEGACION', true, NOW(), NOW()
         WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'NAVEGACION' AND deleted_at IS NULL);
    END IF;
END $$;
