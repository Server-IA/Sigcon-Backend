-- HU-AU-08 E4 + HU-AU-09 E5 (2026-04-28)
-- 1) Tabla audit_findings: hallazgos con flujo ABIERTO/EN_REVISION/CERRADO
-- 2) Columna journal_entries.audit_log_id para FK bidireccional
-- 3) Menu CG/AU para hallazgos en frontend

-- 1) audit_findings -------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_findings (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
    audit_log_id    BIGINT NOT NULL REFERENCES audit_logs(id) ON DELETE RESTRICT,
    title           VARCHAR(200) NOT NULL,
    description     VARCHAR(2000),
    status          VARCHAR(20) NOT NULL DEFAULT 'ABIERTO',
    severity        VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    opened_by       VARCHAR(100),
    assigned_to     VARCHAR(100),
    closed_by       VARCHAR(100),
    resolution      VARCHAR(2000),
    opened_at       TIMESTAMP,
    review_started_at TIMESTAMP,
    closed_at       TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP NULL,
    CONSTRAINT ck_audit_findings_status CHECK (status IN ('ABIERTO','EN_REVISION','CERRADO')),
    CONSTRAINT ck_audit_findings_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_audit_findings_company ON audit_findings(company_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_audit_findings_log     ON audit_findings(audit_log_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_audit_findings_status  ON audit_findings(status, created_at DESC) WHERE deleted_at IS NULL;

-- 2) journal_entries.audit_log_id (FK bidireccional, nullable) -----------
ALTER TABLE journal_entries
    ADD COLUMN IF NOT EXISTS audit_log_id BIGINT NULL;

-- Sin FK constraint formal porque audit_logs.company_id puede ser NULL para
-- eventos de plataforma; mantenerlo como referencia logica para evitar
-- conflictos con multi-tenant.
CREATE INDEX IF NOT EXISTS idx_je_audit_log ON journal_entries(audit_log_id) WHERE audit_log_id IS NOT NULL;

-- 3) Menu de hallazgos en modulo Auditoria --------------------------------
DO $$
DECLARE v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules WHERE name = 'Auditoria' AND deleted_at IS NULL LIMIT 1;
    IF v_module_id IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Hallazgos', 'ri-flag-2-line', 'hallazgos', 60, v_module_id, 'ACTIVE', 'AU_FINDINGS', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'AU_FINDINGS' AND deleted_at IS NULL);
    END IF;
END $$;

-- Comentarios para auditoria forense
COMMENT ON TABLE audit_findings IS 'HU-AU-08 E4: Hallazgos de auditoria con flujo ABIERTO/EN_REVISION/CERRADO';
COMMENT ON COLUMN journal_entries.audit_log_id IS 'HU-AU-09 E5: FK bidireccional al log de auditoria que registro la creacion del JE';
