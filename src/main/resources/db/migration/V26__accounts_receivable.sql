-- ============================================================================
-- V26: Modulo Cuentas por Cobrar (AR) - Bloque 1
-- Cubre HUs: AR-01A, AR-01B, AR-04, AR-11, AR-13
-- Tablas: sales_invoices, sales_invoice_lines
-- Fecha: 2026-04-13
-- ============================================================================

-- 1. Tabla de facturas de venta (FV)
-- ============================================================================
CREATE TABLE IF NOT EXISTS sales_invoices (
    id BIGSERIAL PRIMARY KEY,
    invoice_number VARCHAR(30) NOT NULL,
    third_party_id BIGINT NOT NULL REFERENCES third_parties(id),
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    currency_id BIGINT REFERENCES cfg_currency_types(id),
    exchange_rate NUMERIC(19,6) NOT NULL DEFAULT 1,
    payment_form_id BIGINT REFERENCES payment_forms(id),
    subtotal NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_tax NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_withholding NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    balance_due NUMERIC(19,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    notes VARCHAR(1000),
    resolution_number VARCHAR(100),
    cufe VARCHAR(200),
    xml_sent BOOLEAN NOT NULL DEFAULT FALSE,
    journal_entry_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

-- V10-D: reemplazado por UNIQUE(company_id, invoice_number). Legacy neutralizado.
-- CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_invoice_number
--     ON sales_invoices (invoice_number) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_sales_invoice_third_party
    ON sales_invoices (third_party_id);

CREATE INDEX IF NOT EXISTS idx_sales_invoice_date
    ON sales_invoices (invoice_date);

-- 2. Tabla de lineas de factura de venta
-- ============================================================================
CREATE TABLE IF NOT EXISTS sales_invoice_lines (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES sales_invoices(id),
    item_id BIGINT REFERENCES assets(id),
    description VARCHAR(500),
    quantity NUMERIC(19,4) NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    discount NUMERIC(19,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(19,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    withholding_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    total NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sales_invoice_lines_invoice
    ON sales_invoice_lines (invoice_id);

-- 3. Crear modulo "Cuentas por Cobrar" y menus
-- ============================================================================
DO $$
DECLARE
    v_module_id BIGINT;
BEGIN
    INSERT INTO modules (name, description, url, icon, position, status, created_at, updated_at)
    SELECT 'Cuentas por Cobrar', 'Gestion de facturas de venta y recaudos', 'cuentas-por-cobrar', 'ri-bill-line', 8, 'ACTIVE', NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM modules WHERE name = 'Cuentas por Cobrar' AND deleted_at IS NULL
    );

    SELECT id INTO v_module_id FROM modules
        WHERE name = 'Cuentas por Cobrar' AND deleted_at IS NULL LIMIT 1;

    IF v_module_id IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Facturas de Venta', 'ri-bill-line', 'sales-invoices', 1, v_module_id, 'ACTIVE', 'SALES_INVOICES', true, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM menus WHERE component = 'SALES_INVOICES' AND deleted_at IS NULL
        );
    END IF;
END $$;
