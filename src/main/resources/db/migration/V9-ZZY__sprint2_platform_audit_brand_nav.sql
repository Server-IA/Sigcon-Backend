-- Sprint 2 — HU-PA-PLAT-08 + HU-PA-PLAT-07 + HU-PA-BRAND-01 + HU-PA-NAV-01
-- (compatible con DataInitializer; solo dollar quotes anonimos para que el
--  splitter detecte correctamente los limites de funciones)

-- ===========================================================================
-- HU-PA-PLAT-08: tabla audit_log_platform inmutable + indices
-- ===========================================================================
CREATE TABLE IF NOT EXISTS audit_log_platform (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    actor_user_id BIGINT,
    actor_email VARCHAR(255),
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(60),
    target_id VARCHAR(120),
    target_label VARCHAR(255),
    payload_json JSONB,
    remote_ip VARCHAR(64),
    user_agent VARCHAR(500),
    duration_ms BIGINT
);

CREATE INDEX IF NOT EXISTS idx_audit_log_platform_occurred ON audit_log_platform (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_platform_action   ON audit_log_platform (action);
CREATE INDEX IF NOT EXISTS idx_audit_log_platform_actor    ON audit_log_platform (actor_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_platform_target   ON audit_log_platform (target_type, target_id);

-- HU-PA-PLAT-08 E5: trigger que bloquea UPDATE y DELETE
CREATE OR REPLACE FUNCTION audit_log_platform_block_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_log_platform es inmutable. Solo INSERT permitido (HU-PA-PLAT-08 E5).';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_log_platform_no_update ON audit_log_platform;
CREATE TRIGGER trg_audit_log_platform_no_update
BEFORE UPDATE ON audit_log_platform
FOR EACH ROW EXECUTE FUNCTION audit_log_platform_block_mutation();

DROP TRIGGER IF EXISTS trg_audit_log_platform_no_delete ON audit_log_platform;
CREATE TRIGGER trg_audit_log_platform_no_delete
BEFORE DELETE ON audit_log_platform
FOR EACH ROW EXECUTE FUNCTION audit_log_platform_block_mutation();

-- ===========================================================================
-- HU-PA-PLAT-07: must_change_password en users (PLATFORM_ADMIN secundarios)
-- ===========================================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- ===========================================================================
-- HU-PA-BRAND-01: brand_config en companies (JSONB)
-- ===========================================================================
ALTER TABLE companies ADD COLUMN IF NOT EXISTS brand_config JSONB;

-- ===========================================================================
-- HU-PA-NAV-01: module_order en companies (JSONB array de IDs)
-- ===========================================================================
ALTER TABLE companies ADD COLUMN IF NOT EXISTS module_order JSONB;

-- ===========================================================================
-- Permisos: asignar PAR.IDENTIDAD_VISUAL.* y PAR.NAVEGACION.EDITAR a ADMIN_EMPRESA
-- ===========================================================================
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE r.name='ADMIN_EMPRESA' AND r.deleted_at IS NULL
   AND p.deleted_at IS NULL
   AND p.code IN ('PAR.IDENTIDAD_VISUAL.EDITAR','PAR.IDENTIDAD_VISUAL.VER','PAR.NAVEGACION.EDITAR')
ON CONFLICT DO NOTHING;
