-- V9-ZZQ - 2026-04-28
-- HU-AP-22: Submodulo Devoluciones de Mercancia + HU-AP-23: Carga Masiva Facturas
-- Idempotente: WHERE NOT EXISTS sobre component.

DO $$
DECLARE
    v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules
        WHERE name = 'Cuentas por Pagar' AND deleted_at IS NULL LIMIT 1;

    IF v_module_id IS NOT NULL THEN
        -- HU-AP-22: Devoluciones (path nuevo, reusa GoodsReceipt con status REJECTED/RETURNED)
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Devoluciones', 'ri-arrow-go-back-line', 'goods-returns', 8,
               v_module_id, 'ACTIVE', 'AP_GOODS_RETURNS', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus
                          WHERE component = 'AP_GOODS_RETURNS' AND deleted_at IS NULL);

        -- HU-AP-23: Carga masiva facturas (path nuevo, llama POST /invoices/bulk/store)
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Carga Masiva Facturas', 'ri-upload-cloud-2-line', 'invoices-bulk', 9,
               v_module_id, 'ACTIVE', 'AP_INVOICES_BULK', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus
                          WHERE component = 'AP_INVOICES_BULK' AND deleted_at IS NULL);
    END IF;
END $$;
