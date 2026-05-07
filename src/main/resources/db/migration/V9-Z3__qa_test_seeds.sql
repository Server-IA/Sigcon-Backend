-- =============================================================================
-- V9-Z3: Seeds para QA (Bloque I-9, 2026-04-22)
--
-- Crea datos de prueba idempotentes para acelerar el pipeline de QA:
--   - 2 empresas adicionales (SIGCON DEMO ya existe)
--   - 1 admin + 1 contador + 1 auditor por empresa (3 usuarios tenant por empresa)
--   - 1 cliente + 1 proveedor por empresa (terceros demo)
--   - Auto-provision (periodos, mapeos, conceptos NOM) via _tenant_auto_provision
--
-- Todos los usuarios test comparten contrasenia: Passw0rd!
-- (hash bcrypt reutilizado de ana2@acme.test — cost 10)
--
-- Idempotente: WHERE NOT EXISTS / ON CONFLICT DO NOTHING en todos los inserts.
-- Re-ejecutable sin efectos secundarios.
-- =============================================================================

-- ============================================================================
-- 1. Empresas test (SIGCON DEMO id=1 ya existe)
-- ============================================================================
INSERT INTO companies (business_name, nit, dv, legal_representative, email, phone, address, status, created_at, updated_at)
SELECT 'ACME DEMO SAS', '900100200', '3', 'Carlos Mendoza', 'contacto@acme.demo', '3001112233', 'Calle 10 # 15-30 Bogota', 'ACTIVE', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM companies WHERE nit = '900100200' AND deleted_at IS NULL);

INSERT INTO companies (business_name, nit, dv, legal_representative, email, phone, address, status, created_at, updated_at)
SELECT 'CONTADOR TEST SAS', '800500600', '7', 'Maria Rodriguez', 'contacto@contador.test', '3014445566', 'Carrera 50 # 20-15 Medellin', 'ACTIVE', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM companies WHERE nit = '800500600' AND deleted_at IS NULL);

-- ============================================================================
-- 2. Auto-provision de recursos base (periodos, mapeos PUC, conceptos NOM, parameters)
-- ============================================================================
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT id, business_name FROM companies WHERE deleted_at IS NULL AND status = 'ACTIVE'
    LOOP
        PERFORM _tenant_auto_provision(c.id, EXTRACT(YEAR FROM CURRENT_DATE)::INT);
        RAISE NOTICE 'V9-Z3: auto-provision company_id=% (%)', c.id, c.business_name;
    END LOOP;
END $$;

-- ============================================================================
-- 3. Usuarios test (3 por empresa: admin, contador, auditor)
-- Password: Passw0rd! (hash bcrypt cost 10)
-- ============================================================================
DO $$
DECLARE
    v_pass TEXT := '$2a$10$BCLWV2zEWlOnAhn/td3jguIuwAlPjYSQlzBI95qVpieF0VpBxN1Mq';
    c RECORD;
    v_role_admin_id BIGINT;
    v_role_contador_id BIGINT;
    v_role_auditor_id BIGINT;
    v_user_id BIGINT;
    v_suffix TEXT;
BEGIN
    -- Resolver roles globales
    SELECT id INTO v_role_admin_id FROM roles WHERE name = 'ADMIN' AND deleted_at IS NULL;
    SELECT id INTO v_role_contador_id FROM roles WHERE name = 'CONTADOR' AND deleted_at IS NULL;
    SELECT id INTO v_role_auditor_id FROM roles WHERE name = 'AUDITOR' AND deleted_at IS NULL;

    -- QA Bloque AU+ (2026-05-06) FIX: V9-Z3 originalmente iteraba sobre TODAS
    -- las companies activas creando users con sufijo "tenantN". Cuando V9-ZZC
    -- crea las 6 empresas QA, V9-Z3 vuelve a correr y agrega
    -- admin@tenant4.test, admin@tenant5.test, etc., ENSUCIANDO el dataset
    -- con users adicionales que confunden al QA.
    --
    -- Ahora SOLO crea users para las 3 empresas legacy con NIT especifico:
    -- 900000000 (SIGCON DEMO), 900100200 (ACME DEMO), 800500600 (CONTADOR TEST).
    -- Las empresas QA tienen sus propios users via V9-ZZC con emails
    -- admin@empresaN.test (un set claro y consistente).
    FOR c IN SELECT id, business_name, nit FROM companies
              WHERE deleted_at IS NULL
                AND nit IN ('900000000', '900100200', '800500600')
              ORDER BY id
    LOOP
        v_suffix := CASE c.nit
            WHEN '900000000' THEN 'sigcondemo'
            WHEN '900100200' THEN 'acmedemo'
            WHEN '800500600' THEN 'contadortest'
            ELSE 'tenant' || c.id
        END;

        -- 3.1 Admin de empresa
        IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@' || v_suffix || '.test' AND deleted_at IS NULL) THEN
            INSERT INTO users (name, lastname, email, username, password, status, failed_login_attempts,
                               company_id, platform_role, created_at, updated_at)
            VALUES ('Admin', upper(v_suffix), 'admin@' || v_suffix || '.test', 'admin.' || v_suffix,
                    v_pass, 'ACTIVE', 0, c.id, NULL, NOW(), NOW())
            RETURNING id INTO v_user_id;
            IF v_role_admin_id IS NOT NULL THEN
                INSERT INTO users_roles (user_id, role_id) VALUES (v_user_id, v_role_admin_id)
                ON CONFLICT DO NOTHING;
            END IF;
        END IF;

        -- 3.2 Contador
        IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'contador@' || v_suffix || '.test' AND deleted_at IS NULL) THEN
            INSERT INTO users (name, lastname, email, username, password, status, failed_login_attempts,
                               company_id, platform_role, created_at, updated_at)
            VALUES ('Contador', upper(v_suffix), 'contador@' || v_suffix || '.test', 'contador.' || v_suffix,
                    v_pass, 'ACTIVE', 0, c.id, NULL, NOW(), NOW())
            RETURNING id INTO v_user_id;
            IF v_role_contador_id IS NOT NULL THEN
                INSERT INTO users_roles (user_id, role_id) VALUES (v_user_id, v_role_contador_id)
                ON CONFLICT DO NOTHING;
            END IF;
        END IF;

        -- 3.3 Auditor (solo lectura)
        IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'auditor@' || v_suffix || '.test' AND deleted_at IS NULL) THEN
            INSERT INTO users (name, lastname, email, username, password, status, failed_login_attempts,
                               company_id, platform_role, created_at, updated_at)
            VALUES ('Auditor', upper(v_suffix), 'auditor@' || v_suffix || '.test', 'auditor.' || v_suffix,
                    v_pass, 'ACTIVE', 0, c.id, NULL, NOW(), NOW())
            RETURNING id INTO v_user_id;
            IF v_role_auditor_id IS NOT NULL THEN
                INSERT INTO users_roles (user_id, role_id) VALUES (v_user_id, v_role_auditor_id)
                ON CONFLICT DO NOTHING;
            END IF;
        END IF;

        RAISE NOTICE 'V9-Z3: usuarios test creados para company_id=% (%)', c.id, c.business_name;
    END LOOP;
END $$;

-- ============================================================================
-- 4. Terceros demo por empresa (1 cliente + 1 proveedor)
-- ============================================================================
DO $$
DECLARE
    c RECORD;
    v_status_activo BIGINT;
    v_seq INT;
BEGIN
    SELECT id INTO v_status_activo FROM third_party_status_catalog WHERE name = 'ACTIVO' LIMIT 1;
    IF v_status_activo IS NULL THEN
        RAISE NOTICE 'V9-Z3: third_party_status_catalog.ACTIVO no existe, skip terceros';
        RETURN;
    END IF;

    FOR c IN SELECT id FROM companies WHERE deleted_at IS NULL ORDER BY id
    LOOP
        -- Cliente demo
        IF NOT EXISTS (SELECT 1 FROM third_parties
                        WHERE company_id = c.id AND third_party_code = 'CLI-DEMO-001' AND deleted_at IS NULL) THEN
            INSERT INTO third_parties (third_party_code, business_name, nit, dv, status_id, source,
                                       company_id, created_at, updated_at)
            VALUES ('CLI-DEMO-001', 'CLIENTE DEMO QA SAS', '900' || lpad((c.id * 100 + 1)::text, 6, '0'),
                    '5', v_status_activo, 'MANUAL', c.id, NOW(), NOW());
        END IF;

        -- Proveedor demo
        IF NOT EXISTS (SELECT 1 FROM third_parties
                        WHERE company_id = c.id AND third_party_code = 'PROV-DEMO-001' AND deleted_at IS NULL) THEN
            INSERT INTO third_parties (third_party_code, business_name, nit, dv, status_id, source,
                                       company_id, created_at, updated_at)
            VALUES ('PROV-DEMO-001', 'PROVEEDOR DEMO QA SAS', '800' || lpad((c.id * 100 + 1)::text, 6, '0'),
                    '2', v_status_activo, 'MANUAL', c.id, NOW(), NOW());
        END IF;

        RAISE NOTICE 'V9-Z3: terceros demo creados para company_id=%', c.id;
    END LOOP;
END $$;

-- ============================================================================
-- 5. Bancos demo por empresa (1 banco + 1 cuenta bancaria + 1 caja)
-- ============================================================================
DO $$
DECLARE
    c RECORD;
    v_country_co BIGINT;
    v_bank_id BIGINT;
BEGIN
    SELECT id INTO v_country_co FROM countries WHERE name ILIKE 'COLOMBIA' LIMIT 1;
    IF v_country_co IS NULL THEN
        RAISE NOTICE 'V9-Z3: country COLOMBIA no existe, skip bancos';
        RETURN;
    END IF;

    FOR c IN SELECT id FROM companies WHERE deleted_at IS NULL ORDER BY id
    LOOP
        -- Banco demo (BANCOLOMBIA)
        IF NOT EXISTS (SELECT 1 FROM banks
                        WHERE company_id = c.id AND code = 'BC-QA' AND deleted_at IS NULL) THEN
            INSERT INTO banks (code, name, name_short, nit, code_ach, swift, type_bank, status,
                               country_id, company_id, created_at, updated_at)
            VALUES ('BC-QA', 'BANCOLOMBIA DEMO QA', 'BC-DEMO',
                    '890' || lpad((c.id * 100)::text, 6, '0'), 'ACH-' || c.id,
                    'SWIFT-' || c.id, 'COMMERCIAL', 'ACTIVE', v_country_co, c.id, NOW(), NOW())
            RETURNING id INTO v_bank_id;
            RAISE NOTICE 'V9-Z3: banco demo id=% creado para company_id=%', v_bank_id, c.id;
        END IF;
    END LOOP;
END $$;

-- ============================================================================
-- 6. Comentario final para trazabilidad en logs
-- ============================================================================
DO $$
DECLARE v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM users WHERE email LIKE '%@%.test' AND deleted_at IS NULL;
    RAISE NOTICE 'V9-Z3: seeds completados. Usuarios de test activos: %', v_count;
END $$;
