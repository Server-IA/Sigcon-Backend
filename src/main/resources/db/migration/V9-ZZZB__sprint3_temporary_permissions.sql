-- Sprint 3 - HU-PA-13 a HU-PA-17: permisos temporales
-- (compatible con DataInitializer; solo dollar quotes anonimos)
CREATE TABLE IF NOT EXISTS temporary_permissions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    permission_code VARCHAR(120) NOT NULL,
    granted_by_user_id BIGINT,
    granted_by_email VARCHAR(255),
    justification VARCHAR(500) NOT NULL,
    start_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    end_date TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by_user_id BIGINT,
    revoked_by_email VARCHAR(255),
    revocation_reason VARCHAR(500),
    expired_notified_24h BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_temp_perm_status CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
    CONSTRAINT ck_temp_perm_dates CHECK (end_date > start_date),
    CONSTRAINT fk_temp_perm_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_temp_perm_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_temp_perm_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)
);

CREATE INDEX IF NOT EXISTS idx_temp_perm_user_status ON temporary_permissions(user_id, status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_temp_perm_end_date ON temporary_permissions(end_date) WHERE status = 'ACTIVE' AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_temp_perm_company ON temporary_permissions(company_id) WHERE deleted_at IS NULL;

-- Permisos del glosario v2 ya tienen PAR.PERMISOS_TEMPORALES.ASIGNAR / VER / REVOCAR (asumido)
-- Si no existieran, los creamos:
INSERT INTO permissions(code, name, description, type, module_id, created_at, updated_at)
SELECT v.code, v.name, v.description, v.ptype, m.id, NOW(), NOW()
  FROM (VALUES
    ('PAR.PERMISOS_TEMPORALES.ASIGNAR','Asignar permiso temporal a usuario','HU-PA-13','UPDATE'),
    ('PAR.PERMISOS_TEMPORALES.REVOCAR','Revocar permiso temporal','HU-PA-14','DELETE'),
    ('PAR.PERMISOS_TEMPORALES.VER','Ver permisos temporales (historial)','HU-PA-16','READ')
  ) AS v(code,name,description,ptype)
  CROSS JOIN (SELECT id FROM modules WHERE name ILIKE 'parametr%' AND deleted_at IS NULL LIMIT 1) m
 WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code AND p.deleted_at IS NULL);

-- Asignar a ADMIN_EMPRESA + PLATFORM_ADMIN (ADMIN_EMPRESA gestiona; PLATFORM_ADMIN ve)
INSERT INTO roles_permissions(role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE r.name='ADMIN_EMPRESA' AND r.deleted_at IS NULL
   AND p.deleted_at IS NULL
   AND p.code IN ('PAR.PERMISOS_TEMPORALES.ASIGNAR','PAR.PERMISOS_TEMPORALES.REVOCAR','PAR.PERMISOS_TEMPORALES.VER')
ON CONFLICT DO NOTHING;
