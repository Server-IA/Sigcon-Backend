-- =====================================================================
-- Menús de UI para cerrar los huecos de FASE 1 + HU-068:
--   * Pre-procesamiento (BNK-HU-068)          -> PREPROCESAMIENTO        (Bancos y Cajas)
--   * Soportes de Conciliación (BNK-HU-062/063) -> SOPORTES_CONCILIACION (Bancos y Cajas)
--   * Integridad del Log (BNK-HU-065)         -> AU_INTEGRIDAD           (Auditoría)
-- ADITIVO (R2/R3), idempotente. No toca menús existentes ni otros módulos.
-- =====================================================================

DO $$
DECLARE
    v_bnk BIGINT;
    v_au  BIGINT;
BEGIN
    SELECT id INTO v_bnk FROM modules WHERE name = 'Bancos y Cajas' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_au  FROM modules WHERE name = 'Auditoría'      AND deleted_at IS NULL LIMIT 1;

    IF v_bnk IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'Pre-procesamiento', 'ri-magic-line', 'preprocesamiento', 16, v_bnk, 'ACTIVE', 'PREPROCESAMIENTO', true, 'BNK.CUENTAS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'PREPROCESAMIENTO' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'Soportes de Conciliación', 'ri-folder-shield-2-line', 'soportes-conciliacion', 17, v_bnk, 'ACTIVE', 'SOPORTES_CONCILIACION', true, 'BNK.CUENTAS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'SOPORTES_CONCILIACION' AND deleted_at IS NULL);
    END IF;

    IF v_au IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'Integridad del Log', 'ri-shield-keyhole-line', 'integridad', 30, v_au, 'ACTIVE', 'AU_INTEGRIDAD', true, 'AU.LOG.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'AU_INTEGRIDAD' AND deleted_at IS NULL);
    END IF;
END $$;
