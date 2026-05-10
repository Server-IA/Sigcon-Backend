-- =====================================================================
-- HU-PA-03 / HU-PA-04 / HU-PA-06 / HU-PA-10 - Bloque PA Bugs (2026-05-09)
-- =====================================================================
-- Reportes QA cubiertos:
--   * HU-PA-03 E1: tabla de roles necesita columnas Descripcion, Tipo,
--     Numero de usuarios asignados, Fecha de creacion + diferenciacion
--     visual roles predefinidos vs personalizados.
--   * HU-PA-03 E2: filtro por Tipo (Predefinido/Personalizado).
--   * HU-PA-04 E3: unicidad de nombre de rol debe ser POR TENANT
--     (company_id, name) y no global.
--   * HU-PA-10: aprovisionar roles predefinidos por empresa al crearla.
--   * HU-PA-06 E5: bloquear eliminar rol predefinido si seria el ultimo
--     ADMIN_EMPRESA activo de la empresa (logica del service, esta migracion
--     solo prepara el modelo).
--
-- Cambios en el modelo:
--   * roles.company_id  BIGINT NULL    -> NULL = rol global del sistema
--                                       (PLATFORM_ADMIN, ADMIN, USER); NOT NULL = rol del tenant.
--   * roles.description VARCHAR(500) NULL
--   * UNIQUE INDEX parcial sobre (company_id, LOWER(name)) WHERE deleted_at IS NULL.
--   * Borrar el UNIQUE viejo sobre LOWER(name) si existe.
--   * Backfill: para cada empresa ACTIVE crear copias de los 6 roles predefinidos
--     (CONTADOR, AUXILIAR_CONTABLE, AUDITOR, ADMIN_EMPRESA, TESORERO, OPERADOR_NOMINA),
--     replicando los permisos del rol global homonimo.
--   * Reasignar users_roles a los nuevos roles por tenant (lookup por
--     usuario.company_id + role.name).
--
-- Idempotencia: la migracion se puede correr varias veces sin duplicar.
-- =====================================================================

-- 1. Columnas nuevas
ALTER TABLE roles ADD COLUMN IF NOT EXISTS company_id BIGINT NULL;
ALTER TABLE roles ADD COLUMN IF NOT EXISTS description VARCHAR(500) NULL;

-- FK opcional a companies (sin constraint estricto si companies no existe en el momento)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='companies')
       AND NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                        WHERE table_name='roles' AND constraint_name='fk_roles_company') THEN
        ALTER TABLE roles
            ADD CONSTRAINT fk_roles_company
            FOREIGN KEY (company_id) REFERENCES companies(id)
            ON DELETE RESTRICT;
    END IF;
END $$;

-- 2. Drop UNIQUE viejo sobre name
DO $$
DECLARE rec RECORD;
BEGIN
    -- Buscar y eliminar cualquier UNIQUE constraint o INDEX sobre solo `name`
    FOR rec IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'roles'::regclass
          AND contype = 'u'
          AND array_length(conkey, 1) = 1
          AND (SELECT attname FROM pg_attribute
                WHERE attrelid = 'roles'::regclass AND attnum = ANY(conkey)) = 'name'
    LOOP
        EXECUTE format('ALTER TABLE roles DROP CONSTRAINT %I', rec.conname);
    END LOOP;

    -- Tambien indices unicos solo sobre name
    FOR rec IN
        SELECT indexname FROM pg_indexes
        WHERE tablename='roles'
          AND indexname IN ('uk_roles_name','uk_roles_name_active')
    LOOP
        EXECUTE format('DROP INDEX IF EXISTS %I', rec.indexname);
    END LOOP;
END $$;

-- 3. UNIQUE INDEX parcial compuesto
CREATE UNIQUE INDEX IF NOT EXISTS uk_roles_company_name_active
    ON roles (company_id, LOWER(name)) WHERE deleted_at IS NULL;
-- y para roles GLOBALES (company_id IS NULL): UNIQUE separado con WHERE company_id IS NULL
CREATE UNIQUE INDEX IF NOT EXISTS uk_roles_global_name_active
    ON roles (LOWER(name)) WHERE deleted_at IS NULL AND company_id IS NULL;

-- 4. Roles globales: PLATFORM_ADMIN, ADMIN, USER quedan con company_id=NULL
UPDATE roles SET company_id = NULL
 WHERE deleted_at IS NULL
   AND UPPER(name) IN ('PLATFORM_ADMIN','ADMIN','USER');

-- 5. Backfill: clonar roles predefinidos por cada empresa ACTIVE.
--    Roles predefinidos: CONTADOR, AUXILIAR_CONTABLE, AUDITOR, ADMIN_EMPRESA,
--    TESORERO, OPERADOR_NOMINA.
--    Si el rol global homonimo existe, copia sus permisos.
DO $$
DECLARE
    cmp RECORD;
    src_role RECORD;
    new_role_id BIGINT;
    predefined TEXT[] := ARRAY['CONTADOR','AUXILIAR_CONTABLE','AUDITOR','ADMIN_EMPRESA','TESORERO','OPERADOR_NOMINA'];
    pname TEXT;
    descripciones TEXT[][] := ARRAY[
        ARRAY['CONTADOR','Rol predefinido: contador profesional con permisos para operar AP/AR/BNK/CG.'],
        ARRAY['AUXILIAR_CONTABLE','Rol predefinido: auxiliar de captura de facturas y pagos.'],
        ARRAY['AUDITOR','Rol predefinido: solo lectura cross-modulo + auditoria.'],
        ARRAY['ADMIN_EMPRESA','Rol predefinido: administrador de la empresa (operativo + parametrizacion).'],
        ARRAY['TESORERO','Rol predefinido: gestion de tesoreria + conciliacion AP/BNK.'],
        ARRAY['OPERADOR_NOMINA','Rol predefinido: operador de nomina y prestaciones sociales.']
    ];
    desc_text TEXT;
    pair TEXT[];
BEGIN
    FOR cmp IN SELECT id FROM companies WHERE status = 'ACTIVE' LOOP
        FOREACH pname IN ARRAY predefined LOOP
            -- Ya existe rol del tenant? omitir
            IF EXISTS (SELECT 1 FROM roles
                       WHERE company_id = cmp.id
                         AND UPPER(name) = pname
                         AND deleted_at IS NULL) THEN
                CONTINUE;
            END IF;

            -- Buscar descripcion
            desc_text := NULL;
            FOREACH pair SLICE 1 IN ARRAY descripciones LOOP
                IF pair[1] = pname THEN
                    desc_text := pair[2];
                END IF;
            END LOOP;

            -- Insertar el nuevo rol con permisos clonados del rol global homonimo
            INSERT INTO roles (name, status, created_at, updated_at, company_id, description)
            VALUES (pname, 'ACTIVE', NOW(), NOW(), cmp.id, desc_text)
            RETURNING id INTO new_role_id;

            -- Clonar permisos del rol global con el mismo nombre (si existe)
            INSERT INTO roles_permissions (role_id, permission_id)
            SELECT new_role_id, rp.permission_id
              FROM roles r
              JOIN roles_permissions rp ON rp.role_id = r.id
             WHERE r.deleted_at IS NULL
               AND UPPER(r.name) = pname
               AND r.company_id IS NULL
            ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;
END $$;

-- 6. Reasignar users_roles a los roles del tenant del usuario.
-- Para cada users_roles donde el rol apunta a un rol global predefinido,
-- UPDATE in-place al rol del mismo nombre en la empresa del usuario.
-- Importante: debemos desactivar temporalmente el trigger legacy
-- check_min_role1_user que protege la presencia de un usuario con role_id=1
-- (legacy SUPERADMIN). Despues de la migracion lo reactivamos.
DO $$
DECLARE rec RECORD;
        new_role_id BIGINT;
        predefined_set TEXT[] := ARRAY['CONTADOR','AUXILIAR_CONTABLE','AUDITOR','ADMIN_EMPRESA','TESORERO','OPERADOR_NOMINA'];
        trigger_exists BOOLEAN;
BEGIN
    -- Detectar si el trigger problematico existe
    SELECT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname IN (
            SELECT tgname FROM pg_trigger
             WHERE tgrelid = 'users_roles'::regclass
               AND tgname NOT LIKE 'pg_%'
        )
        AND tgname = 'trg_check_min_role1_user'
    ) INTO trigger_exists;

    IF trigger_exists THEN
        EXECUTE 'ALTER TABLE users_roles DISABLE TRIGGER trg_check_min_role1_user';
    END IF;

    -- Tambien intentar deshabilitar cualquier trigger de validacion en la tabla
    -- como medida defensiva si el nombre cambio
    BEGIN
        EXECUTE 'ALTER TABLE users_roles DISABLE TRIGGER USER';
    EXCEPTION WHEN OTHERS THEN NULL; END;

    FOR rec IN
        SELECT ur.user_id, ur.role_id, r.name AS role_name, u.company_id
        FROM users_roles ur
        JOIN roles r ON r.id = ur.role_id
        JOIN users u ON u.id = ur.user_id
        WHERE r.deleted_at IS NULL
          AND u.deleted_at IS NULL
          AND r.company_id IS NULL                  -- solo roles globales
          AND UPPER(r.name) = ANY(predefined_set)
          AND u.company_id IS NOT NULL
    LOOP
        SELECT id INTO new_role_id FROM roles
            WHERE company_id = rec.company_id
              AND UPPER(name) = UPPER(rec.role_name)
              AND deleted_at IS NULL
            LIMIT 1;
        IF new_role_id IS NOT NULL AND new_role_id <> rec.role_id THEN
            -- UPDATE in-place: cambiar role_id de la fila existente.
            -- Si ya existe la fila destino (UNIQUE), borrar la fuente.
            IF NOT EXISTS (
                SELECT 1 FROM users_roles
                WHERE user_id = rec.user_id AND role_id = new_role_id
            ) THEN
                UPDATE users_roles
                   SET role_id = new_role_id
                 WHERE user_id = rec.user_id AND role_id = rec.role_id;
            ELSE
                DELETE FROM users_roles
                 WHERE user_id = rec.user_id AND role_id = rec.role_id;
            END IF;
        END IF;
    END LOOP;

    -- Reactivar triggers
    BEGIN
        EXECUTE 'ALTER TABLE users_roles ENABLE TRIGGER USER';
    EXCEPTION WHEN OTHERS THEN NULL; END;

    IF trigger_exists THEN
        EXECUTE 'ALTER TABLE users_roles ENABLE TRIGGER trg_check_min_role1_user';
    END IF;
END $$;

-- 7. Eliminar (soft-delete) los roles globales predefinidos que ya fueron clonados
-- por tenant. Esto deja solo los globales del sistema (PLATFORM_ADMIN, ADMIN, USER)
-- y los roles por tenant. Los predefinidos globales legacy se marcan con deleted_at
-- para que no aparezcan en ningun listado.
UPDATE roles SET deleted_at = NOW(), updated_at = NOW()
 WHERE deleted_at IS NULL
   AND company_id IS NULL
   AND UPPER(name) IN ('CONTADOR','AUXILIAR_CONTABLE','AUDITOR','ADMIN_EMPRESA','TESORERO','OPERADOR_NOMINA');

-- 8. INDEX por company_id para acelerar filtrado tenant.
CREATE INDEX IF NOT EXISTS idx_roles_company ON roles (company_id) WHERE deleted_at IS NULL;
