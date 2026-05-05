-- Sprint 4 — registro de menu Notificaciones por rol (HU-PA-18). Idempotente.
DO $$
DECLARE v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules WHERE name = 'Parametrizacion' AND deleted_at IS NULL LIMIT 1;
    IF v_module_id IS NULL THEN
        SELECT id INTO v_module_id FROM modules WHERE name ILIKE 'parametr%' AND deleted_at IS NULL LIMIT 1;
    END IF;

    IF v_module_id IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Notificaciones por rol', 'ri-notification-3-line', 'notificaciones-rol', 92, v_module_id, 'ACTIVE', 'NOTIFICACIONES_ROL', true, NOW(), NOW()
         WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'NOTIFICACIONES_ROL' AND deleted_at IS NULL);
    END IF;
END $$;
