-- ============================================================================
-- V28: Modulo Cuentas por Cobrar (AR) - Bloque 3
-- Cubre HUs: AR-03 (adjuntos), AR-05 (reportes), AR-06 (overdue),
--            AR-10 (cartera vencida), AR-12 (saldos cliente)
-- Fecha: 2026-04-13
-- ============================================================================

-- 1. Tabla sales_invoice_attachments (AR-03)
-- ============================================================================
CREATE TABLE IF NOT EXISTS sales_invoice_attachments (
    id BIGSERIAL PRIMARY KEY,
    sales_invoice_id BIGINT NOT NULL REFERENCES sales_invoices(id),
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    file_content BYTEA NOT NULL,
    uploaded_by VARCHAR(150),
    uploaded_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sales_invoice_attachment_invoice
    ON sales_invoice_attachments (sales_invoice_id);

-- 2. Menus: Reportes CxC y Cartera Vencida
-- ============================================================================
DO $$
DECLARE
    v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules
        WHERE name = 'Cuentas por Cobrar' AND deleted_at IS NULL LIMIT 1;

    IF v_module_id IS NOT NULL THEN
        -- Menu Reportes CxC
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Reportes CxC', 'ri-file-chart-line', 'reportes-cxc', 5, v_module_id, 'ACTIVE', 'AR_REPORTS', true, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM menus WHERE component = 'AR_REPORTS' AND deleted_at IS NULL
        );

        -- Menu Cartera Vencida
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Cartera Vencida', 'ri-alarm-warning-line', 'cartera-vencida', 6, v_module_id, 'ACTIVE', 'AR_OVERDUE', true, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM menus WHERE component = 'AR_OVERDUE' AND deleted_at IS NULL
        );
    END IF;
END $$;
