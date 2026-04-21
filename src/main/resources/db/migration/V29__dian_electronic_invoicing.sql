-- ============================================================================
-- V29: Facturacion Electronica DIAN - Modulo Cuentas por Cobrar (AR)
-- Cubre HUs: AR-14 (XML+CUFE), AR-15 (envio PSE), AR-16 (PDF+QR), AR-17 (resoluciones)
-- Tablas: dian_resolutions, dian_invoice_submissions
-- ============================================================================

-- 1. Resoluciones DIAN
CREATE TABLE IF NOT EXISTS dian_resolutions (
    id BIGSERIAL PRIMARY KEY,
    resolution_number VARCHAR(100) NOT NULL,
    prefix VARCHAR(10) NOT NULL,
    start_number BIGINT NOT NULL,
    end_number BIGINT NOT NULL,
    current_number BIGINT NOT NULL DEFAULT 0,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    technical_key VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

-- V10-D replaced by uk_dian_res_company_number (company_id, resolution_number)
-- CREATE UNIQUE INDEX IF NOT EXISTS uk_dian_resolution_number
--     ON dian_resolutions (resolution_number) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_dian_resolution_prefix_dates
    ON dian_resolutions (prefix, start_date, end_date);

-- 2. Transmisiones DIAN
CREATE TABLE IF NOT EXISTS dian_invoice_submissions (
    id BIGSERIAL PRIMARY KEY,
    sales_invoice_id BIGINT NOT NULL REFERENCES sales_invoices(id),
    cufe VARCHAR(200),
    xml_content TEXT,
    xml_base64 TEXT,
    submission_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    dian_response TEXT,
    submitted_at TIMESTAMP,
    responded_at TIMESTAMP,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    track_id VARCHAR(60),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dian_submission_invoice
    ON dian_invoice_submissions (sales_invoice_id);

-- 3. Menu "Resoluciones DIAN" bajo el modulo Cuentas por Cobrar
DO $$
DECLARE
    v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules
        WHERE name = 'Cuentas por Cobrar' AND deleted_at IS NULL LIMIT 1;
    IF v_module_id IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status,
                           component, visible, created_at, updated_at)
        SELECT 'Resoluciones DIAN', 'ri-file-shield-2-line', 'resoluciones-dian',
               50, v_module_id, 'ACTIVE', 'DIAN_RESOLUTIONS', true, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM menus
             WHERE component = 'DIAN_RESOLUTIONS' AND deleted_at IS NULL
        );
    END IF;
END $$;
