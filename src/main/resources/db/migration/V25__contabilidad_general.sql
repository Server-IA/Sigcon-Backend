-- ============================================================================
-- V25: Modulo de Contabilidad General (CG) completo
-- Cubre HUs: CG-01 a CG-36
-- Fecha: 2026-04-14
-- ============================================================================

-- 1. Tabla de comprobantes de cierre (CG-010 a CG-012)
-- ============================================================================
CREATE TABLE IF NOT EXISTS cg_closing_entries (
    id BIGSERIAL PRIMARY KEY,
    fiscal_year INTEGER NOT NULL,
    fiscal_month INTEGER,
    closing_type VARCHAR(20) NOT NULL,
    journal_entry_id BIGINT REFERENCES journal_entries(id),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    notes VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cg_closing_year ON cg_closing_entries (fiscal_year, fiscal_month);

-- 2. Agregar campo voucher_type al journal_entries para tipo de comprobante
-- ============================================================================
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS voucher_type VARCHAR(30);
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS payment_form_id BIGINT;
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS payment_source_id BIGINT;

-- 3. Crear modulo CG y sus menus
-- ============================================================================
DO $$
DECLARE
    v_module_id BIGINT;
BEGIN
    -- Crear modulo CG si no existe
    INSERT INTO modules (name, description, url, icon, position, status, created_at, updated_at)
    SELECT 'Contabilidad General', 'Gestion contable, libros oficiales y estados financieros', 'contabilidad', 'ri-calculator-line', 7, 'ACTIVE', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM modules WHERE name = 'Contabilidad General' AND deleted_at IS NULL);

    SELECT id INTO v_module_id FROM modules WHERE name = 'Contabilidad General' AND deleted_at IS NULL LIMIT 1;

    IF v_module_id IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Comprobantes', 'ri-file-text-line', 'comprobantes', 1, v_module_id, 'ACTIVE', 'CG_COMPROBANTES', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'CG_COMPROBANTES' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Periodos Contables', 'ri-calendar-check-line', 'periodos', 2, v_module_id, 'ACTIVE', 'CG_PERIODOS', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'CG_PERIODOS' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Libro Diario', 'ri-book-open-line', 'libro-diario', 3, v_module_id, 'ACTIVE', 'CG_LIBRO_DIARIO', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'CG_LIBRO_DIARIO' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Libro Mayor', 'ri-book-2-line', 'libro-mayor', 4, v_module_id, 'ACTIVE', 'CG_LIBRO_MAYOR', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'CG_LIBRO_MAYOR' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Balance Comprobacion', 'ri-scales-line', 'balance-comprobacion', 5, v_module_id, 'ACTIVE', 'CG_BALANCE_COMPROBACION', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'CG_BALANCE_COMPROBACION' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Estados Financieros', 'ri-bar-chart-box-line', 'estados-financieros', 6, v_module_id, 'ACTIVE', 'CG_ESTADOS_FINANCIEROS', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'CG_ESTADOS_FINANCIEROS' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Cierre Contable', 'ri-lock-line', 'cierre', 7, v_module_id, 'ACTIVE', 'CG_CIERRE', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'CG_CIERRE' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Reportes DIAN', 'ri-government-line', 'reportes-dian', 8, v_module_id, 'ACTIVE', 'CG_REPORTES_DIAN', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'CG_REPORTES_DIAN' AND deleted_at IS NULL);
    END IF;
END $$;
