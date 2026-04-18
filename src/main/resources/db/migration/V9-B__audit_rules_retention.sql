-- V9-B: HU-AU-04 reglas configurables de riesgo + HU-AU-10 retencion + legal hold.
--
-- Agrega:
--   * audit_risk_rules: reglas dinamicas que sobrescriben la severidad
--     automatica por accion. El admin define condiciones (modulo, accion,
--     entityType) y la severidad resultante.
--   * audit_retention_policies: politicas de retencion configurables por
--     modulo + severidad. Define dias_retencion para purga automatica.
--   * audit_logs.legal_hold + .retention_until + .archived_at + .archived_hash:
--     legal hold bloquea purga; retention_until marca cuando vence; archived_at
--     marca cuando fue purgado; archived_hash es el SHA-256 del lote purgado.

-- ==========================================================================
-- 1. Tabla audit_risk_rules
-- ==========================================================================
CREATE TABLE IF NOT EXISTS audit_risk_rules (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    description     VARCHAR(500),
    -- Condiciones: cualquiera puede ser null (= "cualquier valor")
    match_module    VARCHAR(10),
    match_action    VARCHAR(20),
    match_entity_type VARCHAR(100),
    -- Resultado
    severity        VARCHAR(10) NOT NULL,
    -- Prioridad (mayor = se evalua primero)
    priority        INT NOT NULL DEFAULT 100,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_by      VARCHAR(150),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_rule_priority ON audit_risk_rules (priority DESC, enabled);

-- Reglas semilla (HU-AU-04 E1: configuracion inicial sugerida)
INSERT INTO audit_risk_rules (name, description, match_module, match_action, match_entity_type, severity, priority, enabled, created_by, created_at, updated_at)
SELECT 'Cierre contable mensual o anual', 'Cualquier evento de cierre se clasifica CRITICAL', 'CG', 'CREATE', 'ClosingEntry', 'CRITICAL', 200, true, 'system', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM audit_risk_rules WHERE name='Cierre contable mensual o anual');

INSERT INTO audit_risk_rules (name, description, match_module, match_action, match_entity_type, severity, priority, enabled, created_by, created_at, updated_at)
SELECT 'Anulacion de comprobante contabilizado', 'Reverso de JE = HIGH', 'CG', 'DELETE', 'JournalEntry', 'HIGH', 180, true, 'system', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM audit_risk_rules WHERE name='Anulacion de comprobante contabilizado');

INSERT INTO audit_risk_rules (name, description, match_module, match_action, match_entity_type, severity, priority, enabled, created_by, created_at, updated_at)
SELECT 'Eliminacion de tercero', 'DELETE TER se eleva a HIGH', 'TER', 'DELETE', NULL, 'HIGH', 150, true, 'system', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM audit_risk_rules WHERE name='Eliminacion de tercero');

INSERT INTO audit_risk_rules (name, description, match_module, match_action, match_entity_type, severity, priority, enabled, created_by, created_at, updated_at)
SELECT 'Login del sistema', 'Eventos de login son LOW', NULL, 'LOGIN', NULL, 'LOW', 100, true, 'system', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM audit_risk_rules WHERE name='Login del sistema');

INSERT INTO audit_risk_rules (name, description, match_module, match_action, match_entity_type, severity, priority, enabled, created_by, created_at, updated_at)
SELECT 'Cualquier eliminacion en AP', 'DELETE en AP escala a HIGH', 'AP', 'DELETE', NULL, 'HIGH', 140, true, 'system', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM audit_risk_rules WHERE name='Cualquier eliminacion en AP');


-- ==========================================================================
-- 2. Tabla audit_retention_policies (HU-AU-10 E1)
-- ==========================================================================
CREATE TABLE IF NOT EXISTS audit_retention_policies (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(150) NOT NULL,
    description         VARCHAR(500),
    -- Filtros opcionales (null = aplica a todo)
    match_module        VARCHAR(10),
    match_severity      VARCHAR(10),
    -- Configuracion de retencion
    retention_days      INT NOT NULL,
    legal_basis         VARCHAR(255),
    enabled             BOOLEAN NOT NULL DEFAULT true,
    created_by          VARCHAR(150),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP NULL
);

-- Politicas semilla (Decreto 2649/1993 Art. 134: 10 anios; Habeas Data 5 anios)
INSERT INTO audit_retention_policies (name, description, match_module, match_severity, retention_days, legal_basis, enabled, created_by, created_at, updated_at)
SELECT 'Logs CRITICOS - 10 anios', 'Eventos CRITICAL se retienen 10 anios (Decreto 2649/1993 Art. 134)', NULL, 'CRITICAL', 3650, 'Decreto 2649/1993 Art. 134', true, 'system', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM audit_retention_policies WHERE name='Logs CRITICOS - 10 anios');

INSERT INTO audit_retention_policies (name, description, match_module, match_severity, retention_days, legal_basis, enabled, created_by, created_at, updated_at)
SELECT 'Logs ALTO - 5 anios', 'Eventos HIGH se retienen 5 anios (Estatuto Tributario)', NULL, 'HIGH', 1825, 'Estatuto Tributario Art. 632', true, 'system', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM audit_retention_policies WHERE name='Logs ALTO - 5 anios');

INSERT INTO audit_retention_policies (name, description, match_module, match_severity, retention_days, legal_basis, enabled, created_by, created_at, updated_at)
SELECT 'Logs MEDIO/BAJO - 2 anios', 'Eventos MEDIUM y LOW se retienen 2 anios', NULL, 'MEDIUM', 730, 'Politica interna', true, 'system', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM audit_retention_policies WHERE name='Logs MEDIO/BAJO - 2 anios');

INSERT INTO audit_retention_policies (name, description, match_module, match_severity, retention_days, legal_basis, enabled, created_by, created_at, updated_at)
SELECT 'Logs BAJO - 1 anio', 'Eventos LOW (login/view/export) se retienen 1 anio', NULL, 'LOW', 365, 'Politica interna', true, 'system', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM audit_retention_policies WHERE name='Logs BAJO - 1 anio');


-- ==========================================================================
-- 3. Campos nuevos en audit_logs (HU-AU-10 E2/E3/E5/E7)
-- ==========================================================================
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS legal_hold        BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS legal_hold_reason VARCHAR(500);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS retention_until   TIMESTAMP NULL;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS archived_at       TIMESTAMP NULL;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS archived_hash     VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_audit_legal_hold ON audit_logs (legal_hold) WHERE legal_hold = true;
CREATE INDEX IF NOT EXISTS idx_audit_retention_until ON audit_logs (retention_until);


-- ==========================================================================
-- 4. Tabla audit_purge_records (HU-AU-10 E5/E6: evidencia de purga)
-- ==========================================================================
CREATE TABLE IF NOT EXISTS audit_purge_records (
    id                  BIGSERIAL PRIMARY KEY,
    purge_date          TIMESTAMP NOT NULL DEFAULT NOW(),
    records_purged      INT NOT NULL,
    oldest_purged       TIMESTAMP,
    newest_purged       TIMESTAMP,
    batch_hash          VARCHAR(64) NOT NULL,
    executed_by         VARCHAR(150),
    notes               VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_audit_purge_date ON audit_purge_records (purge_date DESC);


-- ==========================================================================
-- 5. Menus nuevos
-- ==========================================================================
DO $$
DECLARE v_mod_id BIGINT;
BEGIN
    SELECT id INTO v_mod_id FROM modules
      WHERE name = 'Auditoría' AND deleted_at IS NULL LIMIT 1;
    IF v_mod_id IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Reglas de Riesgo', 'ri-flag-line', 'reglas-riesgo', 40,
               v_mod_id, 'ACTIVE', 'AU_RISK_RULES', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component='AU_RISK_RULES' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Retencion y Purga', 'ri-archive-line', 'retencion', 50,
               v_mod_id, 'ACTIVE', 'AU_RETENTION', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component='AU_RETENTION' AND deleted_at IS NULL);
    END IF;
END $$;
