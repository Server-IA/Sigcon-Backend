-- =====================================================================
-- BNK FASE 7 (DIAN): HU-078 cruce factura electrónica, HU-079 exógena,
-- HU-080 conciliación fiscal. Menús de UI.
-- =====================================================================
-- Las tablas exogena_generaciones y conciliacion_fiscal_notas las crea
-- Hibernate ddl-auto desde las entidades. Esta migración (aditiva,
-- idempotente) sólo registra los 3 menús + índices de apoyo.
-- Prefijo V9-Zzzzza (z minúscula, sufijo 'a') => ordena DESPUES de
-- V9-Zzzzz (mismo prefijo, más largo) y de V9-Z__multi.
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_exogena_gen_ano ON exogena_generaciones (ano_fiscal, formato);
CREATE INDEX IF NOT EXISTS idx_concfiscal_nota_ano ON conciliacion_fiscal_notas (ano_fiscal, partida_key);

DO $$
DECLARE v_bnk BIGINT;
BEGIN
    SELECT id INTO v_bnk FROM modules WHERE name = 'Bancos y Cajas' AND deleted_at IS NULL LIMIT 1;
    IF v_bnk IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'Cruce Factura Electrónica', 'ri-links-line', 'cruce-factura-electronica', 25, v_bnk, 'ACTIVE', 'CRUCE_FE', true, 'BNK.CUENTAS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'CRUCE_FE' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'Exógena DIAN', 'ri-government-line', 'exogena-dian', 26, v_bnk, 'ACTIVE', 'EXOGENA_DIAN', true, 'BNK.CUENTAS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'EXOGENA_DIAN' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'Conciliación Fiscal', 'ri-scales-3-line', 'conciliacion-fiscal', 27, v_bnk, 'ACTIVE', 'CONCILIACION_FISCAL', true, 'BNK.CUENTAS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'CONCILIACION_FISCAL' AND deleted_at IS NULL);
    END IF;
END $$;
