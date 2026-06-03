-- =====================================================================
-- PA v3.0 (Control de Cambios PA, 2026-05-29)
-- =====================================================================
-- PA-RF-PLAT-01: maquina de estados de empresa (PROVISIONING / ERROR) +
--                columnas provisioning_id / idempotency_key / plan / regional_config.
-- PA-RF-10:      aprovisionar los 9 roles predefinidos al crear empresa
--                (antes solo 6). Se agregan REVISOR_FISCAL,
--                SUPERVISOR_CONCILIACION y CONCILIADOR con un baseline de
--                permisos (la matriz fina es un artefacto aparte, ver doc 7.5).
--
-- Orden lexical: 'V9-Zzzzzj' ordena DESPUES de V9-ZZZZC (definicion original
-- de la funcion con 6 roles) y de V9-Z__multi, asi este CREATE OR REPLACE gana.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. PA-RF-PLAT-01: estados PROVISIONING / ERROR en companies.status
-- ---------------------------------------------------------------------
ALTER TABLE companies DROP CONSTRAINT IF EXISTS ck_companies_status;
ALTER TABLE companies ADD CONSTRAINT ck_companies_status
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'PROVISIONING', 'ERROR'));

-- Columnas nuevas (defensivo: Hibernate ddl-auto tambien las crea desde la entidad).
ALTER TABLE companies ADD COLUMN IF NOT EXISTS provisioning_id  VARCHAR(64);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS idempotency_key  VARCHAR(100);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS plan             VARCHAR(50);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS regional_config  TEXT;

CREATE INDEX IF NOT EXISTS idx_companies_idempotency_key
    ON companies (idempotency_key) WHERE idempotency_key IS NOT NULL;

-- ---------------------------------------------------------------------
-- 2. PA-RF-10: funcion de aprovisionamiento con los 9 roles predefinidos
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION _seed_predefined_roles_for_tenant(p_company_id BIGINT)
RETURNS VOID AS $$
DECLARE
    new_role_id BIGINT;
    -- Orden: los 6 originales primero (CONTADOR/AUXILIAR_CONTABLE/AUDITOR son
    -- modelos de clonado), luego los 3 nuevos de conciliacion/fiscal.
    predefined TEXT[] := ARRAY[
        'CONTADOR','AUXILIAR_CONTABLE','AUDITOR','ADMIN_EMPRESA','TESORERO','OPERADOR_NOMINA',
        'REVISOR_FISCAL','SUPERVISOR_CONCILIACION','CONCILIADOR'
    ];
    pname TEXT;
    descripciones TEXT[][] := ARRAY[
        ARRAY['CONTADOR','Rol predefinido: contador profesional con permisos para operar AP/AR/BNK/CG.'],
        ARRAY['AUXILIAR_CONTABLE','Rol predefinido: auxiliar de captura de facturas y pagos.'],
        ARRAY['AUDITOR','Rol predefinido: solo lectura cross-modulo + auditoria.'],
        ARRAY['ADMIN_EMPRESA','Rol predefinido: administrador de la empresa (operativo + parametrizacion).'],
        ARRAY['TESORERO','Rol predefinido: gestion de tesoreria + conciliacion AP/BNK.'],
        ARRAY['OPERADOR_NOMINA','Rol predefinido: operador de nomina y prestaciones sociales.'],
        ARRAY['REVISOR_FISCAL','Rol predefinido: revisoria fiscal (lectura + firma de conciliaciones).'],
        ARRAY['SUPERVISOR_CONCILIACION','Rol predefinido: supervisa y aprueba conciliaciones bancarias.'],
        ARRAY['CONCILIADOR','Rol predefinido: ejecuta la conciliacion bancaria operativa.']
    ];
    desc_text TEXT;
    pair TEXT[];
    src_company_id BIGINT;
    model_for TEXT;
BEGIN
    -- Empresa fuente (otra ACTIVE) para clonar permisos. Preferir SIGCON DEMO (id=1).
    SELECT id INTO src_company_id FROM companies
     WHERE id <> p_company_id AND status = 'ACTIVE' AND deleted_at IS NULL
     ORDER BY (CASE WHEN id = 1 THEN 0 ELSE 1 END), id
     LIMIT 1;

    FOREACH pname IN ARRAY predefined LOOP
        -- Idempotente: si el rol ya existe en el tenant, omitir.
        IF EXISTS (SELECT 1 FROM roles
                   WHERE company_id = p_company_id AND UPPER(name) = pname AND deleted_at IS NULL) THEN
            CONTINUE;
        END IF;

        desc_text := NULL;
        FOREACH pair SLICE 1 IN ARRAY descripciones LOOP
            IF pair[1] = pname THEN desc_text := pair[2]; END IF;
        END LOOP;

        INSERT INTO roles (name, status, created_at, updated_at, company_id, description)
        VALUES (pname, 'ACTIVE', NOW(), NOW(), p_company_id, desc_text)
        RETURNING id INTO new_role_id;

        -- Clonar permisos: prioridad 1 desde la empresa fuente (mismo nombre).
        IF src_company_id IS NOT NULL THEN
            INSERT INTO roles_permissions (role_id, permission_id)
            SELECT new_role_id, rp.permission_id
              FROM roles r JOIN roles_permissions rp ON rp.role_id = r.id
             WHERE r.deleted_at IS NULL AND UPPER(r.name) = pname AND r.company_id = src_company_id
            ON CONFLICT DO NOTHING;
        END IF;

        -- Prioridad 2: rol global homonimo (company_id IS NULL).
        IF NOT EXISTS (SELECT 1 FROM roles_permissions WHERE role_id = new_role_id) THEN
            INSERT INTO roles_permissions (role_id, permission_id)
            SELECT new_role_id, rp.permission_id
              FROM roles r JOIN roles_permissions rp ON rp.role_id = r.id
             WHERE r.deleted_at IS NULL AND UPPER(r.name) = pname AND r.company_id IS NULL
            ON CONFLICT DO NOTHING;
        END IF;

        -- PA-RF-10 punto 2: baseline para los 3 roles nuevos si quedaron sin
        -- permisos (la empresa fuente/global no tenia ese rol). Se clona de un
        -- rol "modelo" del MISMO tenant ya creado en este loop:
        --   REVISOR_FISCAL          <- AUDITOR  (solo lectura + auditoria)
        --   SUPERVISOR_CONCILIACION <- CONTADOR (operativo BNK)
        --   CONCILIADOR             <- AUXILIAR_CONTABLE (operativo basico)
        IF NOT EXISTS (SELECT 1 FROM roles_permissions WHERE role_id = new_role_id) THEN
            model_for := CASE pname
                WHEN 'REVISOR_FISCAL'          THEN 'AUDITOR'
                WHEN 'SUPERVISOR_CONCILIACION' THEN 'CONTADOR'
                WHEN 'CONCILIADOR'             THEN 'AUXILIAR_CONTABLE'
                ELSE NULL END;
            IF model_for IS NOT NULL THEN
                INSERT INTO roles_permissions (role_id, permission_id)
                SELECT new_role_id, rp.permission_id
                  FROM roles r JOIN roles_permissions rp ON rp.role_id = r.id
                 WHERE r.deleted_at IS NULL AND UPPER(r.name) = model_for AND r.company_id = p_company_id
                ON CONFLICT DO NOTHING;
            END IF;
        END IF;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- 3. Backfill: re-ejecutar para todas las empresas ACTIVE (idempotente).
--    Asi las empresas existentes obtienen los 3 roles nuevos con baseline.
-- ---------------------------------------------------------------------
DO $$
DECLARE cmp RECORD;
BEGIN
    FOR cmp IN SELECT id FROM companies WHERE status = 'ACTIVE' AND deleted_at IS NULL LOOP
        PERFORM _seed_predefined_roles_for_tenant(cmp.id);
    END LOOP;
    RAISE NOTICE 'V9-Zzzzzj: estados PROVISIONING/ERROR + 9 roles predefinidos aplicados';
END $$;
