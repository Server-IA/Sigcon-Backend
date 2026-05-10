-- =====================================================================
-- HU-PA-10 (Bloque PA Bug 24, 2026-05-09)
-- =====================================================================
-- Crea la funcion PL/pgSQL `_seed_predefined_roles_for_tenant(p_company_id)`
-- que aprovisiona los 6 roles predefinidos (CONTADOR, AUXILIAR_CONTABLE,
-- AUDITOR, ADMIN_EMPRESA, TESORERO, OPERADOR_NOMINA) en una empresa nueva,
-- clonando los permisos del rol global homonimo (legacy).
--
-- Idempotente: si el rol ya existe en el tenant, no lo duplica.
--
-- La invocara CompanyService.create() despues de provisionTenantDefaults
-- para garantizar que toda empresa nueva nazca con su set de roles listos
-- para usar (HU-PA-10 E1).
-- =====================================================================

CREATE OR REPLACE FUNCTION _seed_predefined_roles_for_tenant(p_company_id BIGINT)
RETURNS VOID AS $$
DECLARE
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
    src_company_id BIGINT;
BEGIN
    -- Buscar empresa fuente (cualquier otra empresa ACTIVE) para clonar permisos.
    -- Preferimos SIGCON DEMO (id=1). Si no existe, tomamos cualquier ACTIVE.
    SELECT id INTO src_company_id FROM companies
     WHERE id <> p_company_id AND status = 'ACTIVE' AND deleted_at IS NULL
     ORDER BY (CASE WHEN id = 1 THEN 0 ELSE 1 END), id
     LIMIT 1;

    FOREACH pname IN ARRAY predefined LOOP
        -- Si el rol ya existe en este tenant, omitir (idempotente)
        IF EXISTS (SELECT 1 FROM roles
                   WHERE company_id = p_company_id
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

        -- Insertar el nuevo rol
        INSERT INTO roles (name, status, created_at, updated_at, company_id, description)
        VALUES (pname, 'ACTIVE', NOW(), NOW(), p_company_id, desc_text)
        RETURNING id INTO new_role_id;

        -- Clonar permisos: prioridad 1 desde la empresa fuente (mismo nombre),
        -- prioridad 2 desde el rol global homonimo (company_id IS NULL).
        IF src_company_id IS NOT NULL THEN
            INSERT INTO roles_permissions (role_id, permission_id)
            SELECT new_role_id, rp.permission_id
              FROM roles r
              JOIN roles_permissions rp ON rp.role_id = r.id
             WHERE r.deleted_at IS NULL
               AND UPPER(r.name) = pname
               AND r.company_id = src_company_id
            ON CONFLICT DO NOTHING;
        END IF;

        -- Si no se copio nada (empresa fuente sin ese rol), fallback a rol global
        IF NOT EXISTS (SELECT 1 FROM roles_permissions WHERE role_id = new_role_id) THEN
            INSERT INTO roles_permissions (role_id, permission_id)
            SELECT new_role_id, rp.permission_id
              FROM roles r
              JOIN roles_permissions rp ON rp.role_id = r.id
             WHERE r.deleted_at IS NULL
               AND UPPER(r.name) = pname
               AND r.company_id IS NULL
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Backfill defensivo: para empresas existentes ACTIVE que NO tengan los roles
-- predefinidos (posibles empresas creadas antes de esta migracion), invocar la
-- funcion. Idempotente.
DO $$
DECLARE cmp RECORD;
BEGIN
    FOR cmp IN SELECT id FROM companies WHERE status = 'ACTIVE' AND deleted_at IS NULL LOOP
        PERFORM _seed_predefined_roles_for_tenant(cmp.id);
    END LOOP;
END $$;
