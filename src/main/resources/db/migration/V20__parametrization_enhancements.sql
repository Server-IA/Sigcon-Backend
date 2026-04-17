-- ============================================================================
-- V20: Mejoras al modulo de Parametrizacion
-- Cubre HUs: RF-01, RF-19, RF-58, RF-34 a RF-40, RF-59
-- Fecha: 2026-04-12
-- ============================================================================

-- 1. Users: contador de intentos fallidos de login (RF-01)
-- ============================================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP NULL;

-- 2. Menus: campos method y menu_type (RF-19)
-- ============================================================================
ALTER TABLE menus ADD COLUMN IF NOT EXISTS method VARCHAR(10) NULL;
ALTER TABLE menus ADD COLUMN IF NOT EXISTS menu_type VARCHAR(50) NULL;

-- 3. PaymentForms: campo is_contado para motor tributario (RF-58)
-- ============================================================================
ALTER TABLE payment_forms ADD COLUMN IF NOT EXISTS is_contado BOOLEAN DEFAULT FALSE;
UPDATE payment_forms SET is_contado = TRUE WHERE code = 'CASH' AND deleted_at IS NULL;

-- 4. Tipos de reporte (RF-34 a RF-37)
-- ============================================================================
CREATE TABLE IF NOT EXISTS report_types (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_report_types_name_active
    ON report_types (name) WHERE deleted_at IS NULL;

-- 5. Plantillas de reporte (RF-38 a RF-40)
-- ============================================================================
CREATE TABLE IF NOT EXISTS report_templates (
    id BIGSERIAL PRIMARY KEY,
    report_type_id BIGINT NOT NULL REFERENCES report_types(id),
    version INTEGER NOT NULL DEFAULT 1,
    file_path VARCHAR(500),
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_report_templates_type_version
    ON report_templates (report_type_id, version) WHERE deleted_at IS NULL;

-- 6. Retenciones del sistema sin Company (RF-59)
-- ============================================================================
CREATE TABLE IF NOT EXISTS system_withholding_assignments (
    id BIGSERIAL PRIMARY KEY,
    withholding_id BIGINT NOT NULL REFERENCES withholdings(id),
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_wh_active
    ON system_withholding_assignments (withholding_id)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

-- 7. Reglas tributarias: campos UVT para motor de retenciones (CFG-RF-09)
-- ============================================================================
ALTER TABLE ruler_tax ADD COLUMN IF NOT EXISTS min_amount_uvt DOUBLE PRECISION NULL;
ALTER TABLE ruler_tax ADD COLUMN IF NOT EXISTS uvt_value_year DOUBLE PRECISION NULL;

-- 8. Eliminar menus de Empresas y Localidades (ya no existen en Fase 0)
-- ============================================================================
UPDATE menus SET deleted_at = NOW()
WHERE component IN ('COMPANIES', 'LOCALIDADES') AND deleted_at IS NULL;

-- 8. Registrar menus para Tipos de Reporte, Plantillas y Retenciones
-- ============================================================================
DO $$
DECLARE
    v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules WHERE name = 'Parametrización' AND deleted_at IS NULL LIMIT 1;
    IF v_module_id IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Tipos de Reporte', 'ri-file-list-3-line', 'report-types', 11, v_module_id, 'ACTIVE', 'REPORT_TYPES', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'REPORT_TYPES' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Plantillas de Reporte', 'ri-file-copy-2-line', 'report-templates', 12, v_module_id, 'ACTIVE', 'REPORT_TEMPLATES', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'REPORT_TEMPLATES' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Retenciones Sistema', 'ri-shield-check-line', 'retenciones-sistema', 13, v_module_id, 'ACTIVE', 'SYSTEM_WITHHOLDINGS', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'SYSTEM_WITHHOLDINGS' AND deleted_at IS NULL);
    END IF;
END $$;
