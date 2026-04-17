-- V9-A: Modulo de Auditoria (AU) - tabla audit_logs append-only + modulo + menus.
--
-- HU-AU-01: Registro inmutable de eventos criticos con hash SHA-256 encadenado.
-- HU-AU-02: Metadatos tecnicos (ip_address, user_agent).
-- HU-AU-09: Vinculacion con asiento contable (journal_entry_id).
--
-- IMPORTANTE: Esta tabla es APPEND-ONLY. No tiene deleted_at. Los registros
-- son inmutables una vez creados. El hash encadenado garantiza la integridad
-- cronologica de la cadena de auditoria.

-- ==========================================================================
-- 1. Tabla audit_logs (append-only, sin deleted_at)
-- ==========================================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    timestamp       TIMESTAMP NOT NULL DEFAULT NOW(),
    user_id         BIGINT,
    user_email      VARCHAR(255),
    action          VARCHAR(20) NOT NULL,
    entity_type     VARCHAR(100),
    entity_id       BIGINT,
    module          VARCHAR(10) NOT NULL,
    severity        VARCHAR(10) NOT NULL,
    description     VARCHAR(500),
    old_values      TEXT,
    new_values      TEXT,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    hash            VARCHAR(64) NOT NULL,
    previous_hash   VARCHAR(64) NOT NULL,
    journal_entry_id BIGINT
);

-- Indices para consultas frecuentes del dashboard y busquedas avanzadas
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_logs (timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_user_id ON audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_module ON audit_logs (module);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_logs (action);
CREATE INDEX IF NOT EXISTS idx_audit_severity ON audit_logs (severity);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_journal_entry ON audit_logs (journal_entry_id);

-- ==========================================================================
-- 2. Modulo Auditoria
-- ==========================================================================
INSERT INTO modules (name, description, icon, url, position, status, created_at, updated_at)
SELECT 'Auditoría', 'Trazabilidad, logs inmutables y dashboard de riesgos',
       'ri-shield-check-line', 'auditoria', 11, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM modules WHERE name = 'Auditoría' AND deleted_at IS NULL
);

-- ==========================================================================
-- 3. Menus del modulo AU
-- ==========================================================================
DO $$
DECLARE v_mod_id BIGINT;
BEGIN
    SELECT id INTO v_mod_id FROM modules
      WHERE name = 'Auditoría' AND deleted_at IS NULL LIMIT 1;
    IF v_mod_id IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Logs de Auditoría', 'ri-file-list-3-line', 'logs', 10,
               v_mod_id, 'ACTIVE', 'AU_LOGS', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'AU_LOGS' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Dashboard', 'ri-dashboard-line', 'dashboard', 20,
               v_mod_id, 'ACTIVE', 'AU_DASHBOARD', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'AU_DASHBOARD' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Exportación', 'ri-download-2-line', 'exportar', 30,
               v_mod_id, 'ACTIVE', 'AU_EXPORT', true, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'AU_EXPORT' AND deleted_at IS NULL);
    END IF;
END $$;
