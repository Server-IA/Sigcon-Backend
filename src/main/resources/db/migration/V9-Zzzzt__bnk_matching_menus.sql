-- =====================================================================
-- BNK-HU-069/070/071/072 — Menús del frontend para el módulo de matching:
--   * Conciliación (Matching)       -> MATCHING_WORKSPACE
--   * Reglas de Clasificación       -> REGLAS_CLASIFICACION
--   * Parámetros de Matching        -> PARAMETROS_MATCHING
-- ADITIVO (R2/R3), idempotente. Bajo el módulo 'Bancos y Cajas'.
-- Gated con required_permission_code='BNK.CUENTAS.VER' (ADMIN_EMPRESA y quien
-- gestione cuentas bancarias ya lo tiene). No toca otros módulos ni menús.
-- =====================================================================

DO $$
DECLARE
    v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules WHERE name = 'Bancos y Cajas' AND deleted_at IS NULL LIMIT 1;
    IF v_module_id IS NOT NULL THEN

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'Conciliación (Matching)', 'ri-git-merge-line', 'matching-workspace', 13, v_module_id, 'ACTIVE', 'MATCHING_WORKSPACE', true, 'BNK.CUENTAS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'MATCHING_WORKSPACE' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'Reglas de Clasificación', 'ri-filter-3-line', 'reglas-clasificacion', 14, v_module_id, 'ACTIVE', 'REGLAS_CLASIFICACION', true, 'BNK.CUENTAS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'REGLAS_CLASIFICACION' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'Parámetros de Matching', 'ri-settings-3-line', 'parametros-matching', 15, v_module_id, 'ACTIVE', 'PARAMETROS_MATCHING', true, 'BNK.CUENTAS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'PARAMETROS_MATCHING' AND deleted_at IS NULL);

    END IF;
END $$;
