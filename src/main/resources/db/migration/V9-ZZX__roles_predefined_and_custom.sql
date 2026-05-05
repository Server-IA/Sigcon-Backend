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

-- 3. Marcar los 7 roles del glosario como predefinidos del sistema
-- (ADMIN_EMPRESA, CONTADOR, AUXILIAR_CONTABLE, TESORERO, AUDITOR,
--  OPERADOR_NOMINA, PLATFORM_ADMIN). Estos NO tienen company_id.
UPDATE roles
   SET is_predefined = true,
       company_id    = NULL,
       description   = CASE name
            WHEN 'ADMIN_EMPRESA'      THEN 'Administrador de la empresa con acceso total a todos los modulos.'
            WHEN 'CONTADOR'           THEN 'Operador contable que gestiona AP, AR, BNK y consultas CG.'
            WHEN 'AUXILIAR_CONTABLE'  THEN 'Captura basica de facturas y pagos en AP/AR.'
            WHEN 'TESORERO'           THEN 'Gestion de bancos, cajas, pagos y cobros.'
            WHEN 'AUDITOR'            THEN 'Acceso de solo lectura global para auditoria.'
            WHEN 'OPERADOR_NOMINA'    THEN 'Operacion del modulo de Nomina (NOM).'
            WHEN 'PLATFORM_ADMIN'     THEN 'Administrador de plataforma multi-tenant.'
            ELSE description
       END,
       updated_at = NOW()
 WHERE name IN ('ADMIN_EMPRESA','CONTADOR','AUXILIAR_CONTABLE','TESORERO',
                'AUDITOR','OPERADOR_NOMINA','PLATFORM_ADMIN')
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
