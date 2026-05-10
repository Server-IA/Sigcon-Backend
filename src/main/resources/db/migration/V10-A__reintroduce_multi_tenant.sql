-- =========================================================================
-- V10-A: Reintroduccion de multi-tenant (N empresas self-service)
-- =========================================================================
--
-- Revierte la decision de Fase 0 (2026-04-12) y Fase 3 (2026-04-14) que
-- habian eliminado multi-tenant para single-tenant. El lider de proyecto
-- exigio que SIGCON vuelva a ser multi-empresa con N empresas self-service
-- (decision 2026-04-19).
--
-- Alcance de este script (Bloque A - Dia 1):
-- 1. Crea tabla `companies` con los campos fiscales + operativos basicos.
-- 2. Inserta la empresa default "SIGCON DEMO" (id=1) con los valores actuales
--    de la tabla `parameters` (categoria COMPANY).
-- 3. Agrega `company_id BIGINT NULL` a las 3 tablas fundacionales:
--    - users
--    - third_parties
--    - parameters
-- 4. Backfill: asigna company_id=1 a todos los registros existentes.
-- 5. El usuario 'superadmin' queda con company_id=NULL + platform_role='PLATFORM_ADMIN'.
-- 6. Alter columna a NOT NULL (excepto para users donde platform admins son NULL).
-- 7. Crea indices (company_id, ...) para performance.
--
-- NO alcance aqui (se hace en Bloques B, C, D, E, F):
-- - company_id en las otras ~27 entidades tenant-scoped.
-- - Hibernate @Filter.
-- - TenantContext thread-local.
-- - JWT con claim companyId (Dia 2 de Bloque A).
-- - CRUD de Company via REST (Dia 3 de Bloque A).
-- - Auto-creacion de 18 mapeos PUC y 12 periodos al crear empresa.
-- - AAEF con API Key por empresa.
--
-- Idempotente: usa IF NOT EXISTS y comprueba columnas antes de agregarlas.

-- -------------------------------------------------------------------------
-- 1. Tabla companies
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS companies (
    id                      BIGSERIAL       PRIMARY KEY,
    nit                     VARCHAR(20)     NOT NULL,
    dv                      VARCHAR(2),
    business_name           VARCHAR(200)    NOT NULL,
    legal_representative    VARCHAR(200),
    email                   VARCHAR(100),
    phone                   VARCHAR(30),
    address                 VARCHAR(300),
    company_size            VARCHAR(50),
    type_organization_id    BIGINT,
    type_regimen_id         BIGINT,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,
    CONSTRAINT ck_companies_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- UNIQUE parcial sobre NIT (solo empresas activas).
CREATE UNIQUE INDEX IF NOT EXISTS uk_companies_nit_active
    ON companies (nit) WHERE deleted_at IS NULL;

-- FKs a catalogos existentes (type_organization, type_regimen).
-- Solo agregar si las tablas existen (para tolerar BDs en distintos estados).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'type_organization')
       AND NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                       WHERE constraint_name = 'fk_companies_type_organization') THEN
        ALTER TABLE companies
          ADD CONSTRAINT fk_companies_type_organization
          FOREIGN KEY (type_organization_id) REFERENCES type_organization(id);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'type_regimen')
       AND NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                       WHERE constraint_name = 'fk_companies_type_regimen') THEN
        ALTER TABLE companies
          ADD CONSTRAINT fk_companies_type_regimen
          FOREIGN KEY (type_regimen_id) REFERENCES type_regimen(id);
    END IF;
END $$;

-- -------------------------------------------------------------------------
-- 2. Seed de empresa default "SIGCON DEMO" (id=1)
--    Lee los valores actuales de parameters categoria COMPANY.
-- -------------------------------------------------------------------------
-- Nota: created_at/updated_at se llenan explicitamente con NOW() porque
-- Hibernate ddl-auto=update elimina los DEFAULT de las columnas al
-- adaptar la tabla a la entidad Company.java (que usa @CreationTimestamp
-- y @UpdateTimestamp a nivel JPA, sin default SQL). Sin este INSERT
-- explicito, re-ejecuciones del script fallarian con NULL constraint.
INSERT INTO companies (
    id, nit, dv, business_name, legal_representative, email, phone,
    company_size, type_organization_id, type_regimen_id, status,
    created_at, updated_at
)
SELECT
    1,
    COALESCE((SELECT value FROM parameters WHERE name = 'COMPANY_NIT'           AND deleted_at IS NULL), '900000000'),
    COALESCE((SELECT value FROM parameters WHERE name = 'COMPANY_DV'            AND deleted_at IS NULL), '0'),
    'SIGCON DEMO',
    COALESCE((SELECT value FROM parameters WHERE name = 'COMPANY_LEGAL_REPRESENTATIVE' AND deleted_at IS NULL), 'Representante Legal'),
    COALESCE((SELECT value FROM parameters WHERE name = 'COMPANY_EMAIL'         AND deleted_at IS NULL), 'demo@sigcon.co'),
    COALESCE((SELECT value FROM parameters WHERE name = 'COMPANY_PHONE'         AND deleted_at IS NULL), '0000000000'),
    COALESCE((SELECT value FROM parameters WHERE name = 'COMPANY_SIZE'          AND deleted_at IS NULL), '100'),
    NULLIF((SELECT value FROM parameters WHERE name = 'COMPANY_TYPE_ORGANIZATION_ID' AND deleted_at IS NULL), '')::BIGINT,
    NULLIF((SELECT value FROM parameters WHERE name = 'COMPANY_TYPE_REGIMEN_ID'     AND deleted_at IS NULL), '')::BIGINT,
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (SELECT 1 FROM companies WHERE id = 1);

-- Resync de la secuencia para que el proximo INSERT use id=2, 3, ...
SELECT setval(
    pg_get_serial_sequence('companies', 'id'),
    (SELECT COALESCE(MAX(id), 0) FROM companies),
    true
);

-- -------------------------------------------------------------------------
-- 3. users: agregar company_id + platform_role
-- -------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS company_id    BIGINT,
    ADD COLUMN IF NOT EXISTS platform_role VARCHAR(50);

-- Backfill: todos los usuarios van a la empresa default EXCEPTO superadmin
-- y EXCEPTO cualquier otro PLATFORM_ADMIN ya creado (HU-PA-PLAT-07: secundarios
-- con platform_role NOT NULL no deben recibir company_id por el constraint
-- ck_users_tenant_or_platform).
UPDATE users SET company_id = 1
 WHERE company_id IS NULL
   AND username != 'superadmin'
   AND platform_role IS NULL;

-- superadmin se convierte en PLATFORM_ADMIN sin empresa.
UPDATE users
   SET platform_role = 'PLATFORM_ADMIN', company_id = NULL
 WHERE username = 'superadmin' AND platform_role IS NULL;

-- Invariante: un usuario o pertenece a una empresa (company_id NOT NULL,
-- platform_role NULL) o es admin de plataforma (company_id NULL, platform_role
-- NOT NULL). Nunca ambos, nunca ninguno.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_name = 'ck_users_tenant_or_platform') THEN
        ALTER TABLE users
            ADD CONSTRAINT ck_users_tenant_or_platform
            CHECK (
                (company_id IS NOT NULL AND platform_role IS NULL) OR
                (company_id IS NULL     AND platform_role IS NOT NULL)
            );
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_name = 'fk_users_company') THEN
        ALTER TABLE users
            ADD CONSTRAINT fk_users_company
            FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_users_company_id ON users (company_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_users_platform_role ON users (platform_role) WHERE deleted_at IS NULL AND platform_role IS NOT NULL;

-- -------------------------------------------------------------------------
-- 4. third_parties: agregar company_id (tenant-scoped - todos pertenecen a
--    una empresa, no hay terceros de plataforma).
-- -------------------------------------------------------------------------
ALTER TABLE third_parties ADD COLUMN IF NOT EXISTS company_id BIGINT;
UPDATE third_parties SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE third_parties ALTER COLUMN company_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_name = 'fk_third_parties_company') THEN
        ALTER TABLE third_parties
            ADD CONSTRAINT fk_third_parties_company
            FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_third_parties_company_id ON third_parties (company_id) WHERE deleted_at IS NULL;

-- UNIQUE de NIT debe ser por empresa ahora (no global).
-- Primero eliminar cualquier indice UNIQUE global sobre nit.
DO $$
DECLARE r record;
BEGIN
    FOR r IN
        SELECT DISTINCT tc.constraint_name
          FROM information_schema.table_constraints tc
          JOIN information_schema.key_column_usage kcu
            ON tc.constraint_name = kcu.constraint_name
           AND tc.table_schema = kcu.table_schema
         WHERE tc.table_name = 'third_parties'
           AND tc.constraint_type = 'UNIQUE'
           AND kcu.column_name = 'nit'
           AND tc.constraint_name NOT LIKE 'uk_third_parties_nit_company%'
    LOOP
        EXECUTE 'ALTER TABLE third_parties DROP CONSTRAINT IF EXISTS "'
            || r.constraint_name || '" CASCADE';
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_third_parties_nit_company_active
    ON third_parties (company_id, nit) WHERE deleted_at IS NULL;

-- -------------------------------------------------------------------------
-- 5. parameters: agregar company_id (tenant-scoped - cada empresa su config).
-- -------------------------------------------------------------------------
ALTER TABLE parameters ADD COLUMN IF NOT EXISTS company_id BIGINT;
UPDATE parameters SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE parameters ALTER COLUMN company_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_name = 'fk_parameters_company') THEN
        ALTER TABLE parameters
            ADD CONSTRAINT fk_parameters_company
            FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_parameters_company_id ON parameters (company_id) WHERE deleted_at IS NULL;

-- UNIQUE de parameters.name debe ser por empresa ahora.
DO $$
DECLARE r record;
BEGIN
    FOR r IN
        SELECT DISTINCT tc.constraint_name
          FROM information_schema.table_constraints tc
          JOIN information_schema.key_column_usage kcu
            ON tc.constraint_name = kcu.constraint_name
           AND tc.table_schema = kcu.table_schema
         WHERE tc.table_name = 'parameters'
           AND tc.constraint_type = 'UNIQUE'
           AND kcu.column_name = 'name'
           AND tc.constraint_name NOT LIKE 'uk_parameters_name_company%'
    LOOP
        EXECUTE 'ALTER TABLE parameters DROP CONSTRAINT IF EXISTS "'
            || r.constraint_name || '" CASCADE';
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_parameters_name_company_active
    ON parameters (company_id, name) WHERE deleted_at IS NULL;

-- -------------------------------------------------------------------------
-- 6. Verificacion final (se loguea en arranque)
-- -------------------------------------------------------------------------
DO $$
DECLARE
    v_companies INT;
    v_users_with_company INT;
    v_platform_admins INT;
    v_users_invalid INT;
BEGIN
    SELECT COUNT(*) INTO v_companies FROM companies WHERE deleted_at IS NULL;
    SELECT COUNT(*) INTO v_users_with_company FROM users WHERE company_id IS NOT NULL AND deleted_at IS NULL;
    SELECT COUNT(*) INTO v_platform_admins  FROM users WHERE platform_role = 'PLATFORM_ADMIN' AND deleted_at IS NULL;
    SELECT COUNT(*) INTO v_users_invalid    FROM users WHERE deleted_at IS NULL
        AND NOT ((company_id IS NOT NULL AND platform_role IS NULL) OR
                 (company_id IS NULL     AND platform_role IS NOT NULL));

    RAISE NOTICE 'V10-A: companies=%, users_tenant=%, platform_admins=%, users_invalid=%',
        v_companies, v_users_with_company, v_platform_admins, v_users_invalid;

    IF v_users_invalid > 0 THEN
        RAISE EXCEPTION 'V10-A: hay % usuarios con estado invalido (ni empresa ni platform role, o ambos).',
            v_users_invalid;
    END IF;
END $$;
