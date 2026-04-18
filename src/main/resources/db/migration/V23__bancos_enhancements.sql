-- ============================================================================
-- V23: Mejoras al modulo de Bancos y Cajas
-- Cubre HUs: BNK-042 a 049, BNK-050 a 053, validaciones negocio
-- Fecha: 2026-04-13
-- ============================================================================

-- 1. Tabla de arqueos de caja (BNK-042 a 049)
-- ============================================================================
CREATE TABLE IF NOT EXISTS cash_audits (
    id BIGSERIAL PRIMARY KEY,
    cash_id BIGINT NOT NULL REFERENCES cash(id),
    audit_date DATE NOT NULL,
    system_balance NUMERIC(19,2) NOT NULL,
    physical_balance NUMERIC(19,2) NOT NULL,
    difference NUMERIC(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ABIERTO',
    notes VARCHAR(500),
    supervisor_id BIGINT,
    approved_at TIMESTAMP,
    approved_by BIGINT,
    journal_entry_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cash_audits_cash ON cash_audits (cash_id, audit_date DESC);
CREATE INDEX IF NOT EXISTS idx_cash_audits_status ON cash_audits (status);

-- 2. Movimientos financieros: campo clasificacion NIC 7 (BNK-050)
-- ============================================================================
ALTER TABLE financial_movements ADD COLUMN IF NOT EXISTS flow_activity VARCHAR(20);
-- Valores: OPERATIVA, INVERSION, FINANCIACION

-- 3. Menus faltantes: Proyecciones y Arqueos
-- ============================================================================
DO $$
DECLARE
    v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules WHERE name = 'Bancos y Cajas' AND deleted_at IS NULL LIMIT 1;
    IF v_module_id IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Proyecciones Flujo', 'ri-line-chart-line', 'projections', 10, v_module_id, 'ACTIVE', 'CASH_FLOW_PROJECTIONS', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'CASH_FLOW_PROJECTIONS' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Arqueos de Caja', 'ri-calculator-line', 'cash-audits', 11, v_module_id, 'ACTIVE', 'CASH_AUDITS', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'CASH_AUDITS' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Movimientos', 'ri-exchange-funds-line', 'financial-movements', 12, v_module_id, 'ACTIVE', 'FINANCIAL_MOVEMENTS', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'FINANCIAL_MOVEMENTS' AND deleted_at IS NULL);
    END IF;
END $$;
