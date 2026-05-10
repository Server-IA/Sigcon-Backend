-- V9-ZZX (2026-05-01): HU-PA-03 + HU-PA-04 + HU-PA-10
-- Agrega columnas a la tabla roles para distinguir roles predefinidos
-- (globales del sistema, comunes a todas las empresas) de roles
-- personalizados (tenant-scoped, creados por ADMIN_EMPRESA).
--
-- Esquema:
--   is_predefined  BOOLEAN NOT NULL DEFAULT false
--   description    VARCHAR(500) (texto opcional para describir el rol)
--   company_id     BIGINT NULL (NULL si predefinido global; ID empresa si custom)
--
-- Reglas:
--   - is_predefined=true  AND company_id IS NULL  -> rol global del sistema
--   - is_predefined=false AND company_id NOT NULL -> rol custom de la empresa
--   - El listado de roles para un tenant = predefinidos + custom de SU empresa
--
-- Idempotente: usa IF NOT EXISTS / DO blocks.

-- 1. Columnas nuevas
ALTER TABLE roles ADD COLUMN IF NOT EXISTS is_predefined BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE roles ADD COLUMN IF NOT EXISTS description  VARCHAR(500);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS company_id   BIGINT;

-- 2. FK opcional a companies (para custom)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_roles_company'
    ) THEN
        ALTER TABLE roles
            ADD CONSTRAINT fk_roles_company FOREIGN KEY (company_id)
            REFERENCES companies(id);
    END IF;
END $$;

-- QA Bloque PA Bug 4 (HU-PA-04 E3, 2026-05-09): el disenio cambio. Los roles
-- predefinidos NO son globales; son tenant-scoped (cada empresa tiene su copia).
-- V9-ZZZY clona los predefinidos por empresa. El UPDATE original ponia
-- company_id=NULL en todos los roles con esos nombres lo que rompe el modelo.
-- Comentado para no recrear el conflicto. Solo se actualiza la descripcion
-- en los roles que SIGAN siendo globales (PLATFORM_ADMIN).
UPDATE roles
   SET description = CASE name
            WHEN 'PLATFORM_ADMIN' THEN 'Administrador de plataforma multi-tenant.'
            ELSE description
       END,
       updated_at = NOW()
 WHERE name IN ('PLATFORM_ADMIN')
   AND company_id IS NULL
   AND deleted_at IS NULL;

-- 4. UNIQUE indexes:
--   a) Predefinidos globales: name unico (donde company_id IS NULL).
--   b) Custom: (company_id, name) unico (donde company_id IS NOT NULL).
-- El UNIQUE global anterior uk_roles_active(name) bloqueaba HU-PA-04 E3
-- (mismo nombre en distintos tenants). Lo dropeamos y reemplazamos por dos
-- indices parciales.
DROP INDEX IF EXISTS uk_roles_active;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'uk_roles_global_name_active'
    ) THEN
        CREATE UNIQUE INDEX uk_roles_global_name_active
               ON roles (name)
               WHERE deleted_at IS NULL AND company_id IS NULL;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'uk_roles_company_name_active'
    ) THEN
        CREATE UNIQUE INDEX uk_roles_company_name_active
               ON roles (company_id, name)
               WHERE deleted_at IS NULL AND company_id IS NOT NULL;
    END IF;
END $$;

-- 5. Indice para filtrar por is_predefined + tenant en queries
CREATE INDEX IF NOT EXISTS idx_roles_predefined_tenant
       ON roles (is_predefined, company_id)
       WHERE deleted_at IS NULL;
