-- ============================================================================
-- V24: Modulo de Cuentas por Pagar (AP) completo
-- Cubre HUs: AP-01 a AP-25
-- Fecha: 2026-04-13
-- ============================================================================

-- 1. Nuevos estados de factura y campo numero factura proveedor
-- ============================================================================
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS supplier_invoice_number VARCHAR(50);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS balance_due NUMERIC(19,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS journal_entry_id BIGINT;

-- Insertar estados faltantes en invoice_states si no existen
INSERT INTO invoice_states (name, code, block, description, created_at, updated_at)
SELECT * FROM (VALUES
    ('Parcialmente pagado', 'PARTIAL', 'PURCHASE', 'Factura con abonos parciales', NOW(), NOW()),
    ('Anulada', 'VOID', 'PURCHASE', 'Factura anulada', NOW(), NOW()),
    ('Liquidada', 'SETTLED', 'PURCHASE', 'Factura completamente liquidada', NOW(), NOW())
) AS v (name, code, block, description, created_at, updated_at)
WHERE NOT EXISTS (
    SELECT 1 FROM invoice_states WHERE code = v.code AND block = v.block AND deleted_at IS NULL
);

-- Insertar tipos de factura faltantes
INSERT INTO types_invoices (name, code, description, created_at, updated_at)
SELECT * FROM (VALUES
    ('Nota de credito', 'NC', 'Nota de credito sobre factura de compra', NOW(), NOW()),
    ('Nota de debito', 'ND', 'Nota de debito sobre factura de compra', NOW(), NOW())
) AS v (name, code, description, created_at, updated_at)
WHERE NOT EXISTS (
    SELECT 1 FROM types_invoices WHERE code = v.code AND deleted_at IS NULL
);

-- 2. Tabla de pagos y abonos (AP-04, AP-08)
-- ============================================================================
CREATE TABLE IF NOT EXISTS ap_payments (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id),
    amount NUMERIC(19,2) NOT NULL,
    payment_date DATE NOT NULL,
    payment_reference VARCHAR(100),
    payment_method VARCHAR(20) NOT NULL,
    bank_account_id BIGINT REFERENCES bank_accounts(id),
    cash_id BIGINT REFERENCES cash(id),
    check_id BIGINT REFERENCES checks(id),
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    journal_entry_id BIGINT,
    notes VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ap_payments_invoice ON ap_payments (invoice_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ap_payment_ref ON ap_payments (payment_reference) WHERE deleted_at IS NULL AND payment_reference IS NOT NULL;

-- 3. Tabla de anticipos (AP-05)
-- ============================================================================
CREATE TABLE IF NOT EXISTS ap_advances (
    id BIGSERIAL PRIMARY KEY,
    third_party_id BIGINT NOT NULL REFERENCES third_parties(id),
    amount NUMERIC(19,2) NOT NULL,
    advance_date DATE NOT NULL,
    advance_reference VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    applied_invoice_id BIGINT REFERENCES invoices(id),
    applied_amount NUMERIC(19,2),
    applied_at TIMESTAMP,
    bank_account_id BIGINT REFERENCES bank_accounts(id),
    cash_id BIGINT REFERENCES cash(id),
    journal_entry_id BIGINT,
    notes VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ap_advances_third ON ap_advances (third_party_id);

-- 4. Tabla de notas credito/debito (AP-10)
-- ============================================================================
CREATE TABLE IF NOT EXISTS ap_credit_debit_notes (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id),
    note_type VARCHAR(10) NOT NULL,
    note_number VARCHAR(30) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    journal_entry_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ap_notes_invoice ON ap_credit_debit_notes (invoice_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ap_note_number ON ap_credit_debit_notes (note_number) WHERE deleted_at IS NULL;

-- 5. Ordenes de compra (AP-16 a AP-22)
-- ============================================================================
CREATE TABLE IF NOT EXISTS purchase_orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(30) NOT NULL,
    third_party_id BIGINT NOT NULL REFERENCES third_parties(id),
    order_date DATE NOT NULL,
    delivery_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    notes VARCHAR(500),
    approved_by BIGINT,
    approved_at TIMESTAMP,
    rejection_reason VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_po_number ON purchase_orders (order_number) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS purchase_order_lines (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id),
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(19,2) NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    total_line NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

-- 6. Recepciones de bienes/servicios (AP-19, AP-20)
-- ============================================================================
CREATE TABLE IF NOT EXISTS goods_receipts (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id),
    receipt_number VARCHAR(30) NOT NULL,
    receipt_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    invoice_id BIGINT REFERENCES invoices(id),
    notes VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_gr_number ON goods_receipts (receipt_number) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS goods_receipt_lines (
    id BIGSERIAL PRIMARY KEY,
    goods_receipt_id BIGINT NOT NULL REFERENCES goods_receipts(id),
    purchase_order_line_id BIGINT NOT NULL REFERENCES purchase_order_lines(id),
    quantity_received NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

-- 7. Documentos soporte (AP-13)
-- ============================================================================
CREATE TABLE IF NOT EXISTS ap_documents (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT REFERENCES invoices(id),
    purchase_order_id BIGINT REFERENCES purchase_orders(id),
    document_type VARCHAR(30) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT,
    mime_type VARCHAR(100),
    uploaded_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

-- 8. Menus del modulo Cuentas por Pagar
-- ============================================================================
DO $$
DECLARE
    v_module_id BIGINT;
BEGIN
    -- Crear modulo AP si no existe
    INSERT INTO modules (name, description, url, icon, position, status, created_at, updated_at)
    SELECT 'Cuentas por Pagar', 'Gestion de cuentas por pagar', 'accounts-payable', 'ri-money-dollar-box-line', 6, 'ACTIVE', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM modules WHERE name = 'Cuentas por Pagar' AND deleted_at IS NULL);

    SELECT id INTO v_module_id FROM modules WHERE name = 'Cuentas por Pagar' AND deleted_at IS NULL LIMIT 1;

    IF v_module_id IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Facturas Compra', 'ri-file-list-3-line', 'invoices', 1, v_module_id, 'ACTIVE', 'AP_INVOICES', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'AP_INVOICES' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Pagos', 'ri-bank-card-line', 'payments', 2, v_module_id, 'ACTIVE', 'AP_PAYMENTS', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'AP_PAYMENTS' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Anticipos', 'ri-hand-coin-line', 'advances', 3, v_module_id, 'ACTIVE', 'AP_ADVANCES', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'AP_ADVANCES' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Notas Credito/Debito', 'ri-file-edit-line', 'notes', 4, v_module_id, 'ACTIVE', 'AP_NOTES', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'AP_NOTES' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Ordenes de Compra', 'ri-shopping-cart-line', 'purchase-orders', 5, v_module_id, 'ACTIVE', 'AP_PURCHASE_ORDERS', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'AP_PURCHASE_ORDERS' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Recepciones', 'ri-inbox-archive-line', 'receipts', 6, v_module_id, 'ACTIVE', 'AP_RECEIPTS', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'AP_RECEIPTS' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Reportes AP', 'ri-pie-chart-line', 'reports', 7, v_module_id, 'ACTIVE', 'AP_REPORTS', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'AP_REPORTS' AND deleted_at IS NULL);
    END IF;
END $$;
