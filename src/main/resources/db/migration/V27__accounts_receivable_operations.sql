-- ============================================================================
-- V27: Modulo Cuentas por Cobrar (AR) - Bloque 2: Operaciones
-- Cubre HUs: AR-02, AR-07, AR-08, AR-09
-- Tablas: ar_payments, ar_advances, ar_credit_debit_notes
-- Menus: Cobros, Anticipos Clientes, NC/ND Ventas
-- Fecha: 2026-04-13
-- ============================================================================

-- 1. Tabla ar_payments (cobros y abonos a facturas de venta)
-- ============================================================================
CREATE TABLE IF NOT EXISTS ar_payments (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES sales_invoices(id),
    amount NUMERIC(19,2) NOT NULL,
    payment_date DATE NOT NULL,
    payment_reference VARCHAR(100),
    payment_method VARCHAR(20) NOT NULL,
    bank_account_id BIGINT,
    cash_id BIGINT,
    bank_movement_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    journal_entry_id BIGINT,
    notes VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

-- V10-D replaced by uk_ar_payment_ref_company (company_id, payment_reference)
-- CREATE UNIQUE INDEX IF NOT EXISTS uk_ar_payment_reference
--     ON ar_payments (payment_reference)
--     WHERE deleted_at IS NULL AND payment_reference IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ar_payment_invoice
    ON ar_payments (invoice_id);

CREATE INDEX IF NOT EXISTS idx_ar_payment_date
    ON ar_payments (payment_date);

-- 2. Tabla ar_advances (anticipos de clientes)
-- ============================================================================
CREATE TABLE IF NOT EXISTS ar_advances (
    id BIGSERIAL PRIMARY KEY,
    third_party_id BIGINT NOT NULL REFERENCES third_parties(id),
    amount NUMERIC(19,2) NOT NULL,
    applied_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    advance_date DATE NOT NULL,
    advance_reference VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    bank_movement_id BIGINT,
    bank_account_id BIGINT,
    cash_id BIGINT,
    last_applied_at TIMESTAMP,
    journal_entry_id BIGINT,
    notes VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ar_advance_third_party
    ON ar_advances (third_party_id);

CREATE INDEX IF NOT EXISTS idx_ar_advance_status
    ON ar_advances (status);

CREATE INDEX IF NOT EXISTS idx_ar_advance_date
    ON ar_advances (advance_date);

-- 3. Tabla ar_credit_debit_notes (notas credito y debito sobre FV)
-- ============================================================================
CREATE TABLE IF NOT EXISTS ar_credit_debit_notes (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES sales_invoices(id),
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

-- V10-D replaced by uk_ar_notes_company_number (company_id, note_number)
-- CREATE UNIQUE INDEX IF NOT EXISTS uk_ar_note_number
--     ON ar_credit_debit_notes (note_number) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ar_note_invoice
    ON ar_credit_debit_notes (invoice_id);

CREATE INDEX IF NOT EXISTS idx_ar_note_type
    ON ar_credit_debit_notes (note_type);

-- 4. Registrar menus bajo modulo "Cuentas por Cobrar"
-- ============================================================================
DO $$
DECLARE
    v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules
        WHERE name = 'Cuentas por Cobrar' AND deleted_at IS NULL LIMIT 1;

    IF v_module_id IS NOT NULL THEN
        -- Menu Cobros
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Cobros', 'ri-hand-coin-line', 'cobros', 2, v_module_id, 'ACTIVE', 'AR_PAYMENTS', true, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM menus WHERE component = 'AR_PAYMENTS' AND deleted_at IS NULL
        );

        -- Menu Anticipos Clientes
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Anticipos Clientes', 'ri-wallet-3-line', 'anticipos-clientes', 3, v_module_id, 'ACTIVE', 'AR_ADVANCES', true, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM menus WHERE component = 'AR_ADVANCES' AND deleted_at IS NULL
        );

        -- Menu NC/ND Ventas
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'NC/ND Ventas', 'ri-file-edit-line', 'notas-venta', 4, v_module_id, 'ACTIVE', 'AR_NOTES', true, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM menus WHERE component = 'AR_NOTES' AND deleted_at IS NULL
        );
    END IF;
END $$;
