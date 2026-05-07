-- V9-ZZZT — QA Bloque AU+ (2026-05-06)
--
-- ROOT CAUSE: la tabla `users` solo tenia PRIMARY KEY (id) sin UNIQUE sobre
-- email/username. La seed V9-ZZC usaba ON CONFLICT DO NOTHING al crear admins
-- de las 6 empresas QA, pero sin constraint la clausula no aplica y cada
-- re-arranque del backend creaba 18 filas duplicadas (admin/contador/auditor
-- x 6 empresas). Ademas V9-ZZC re-creaba las empresas con ids nuevos cuando
-- las viejas quedaban soft-deleted por V9-ZZA, generando users con
-- company_id apuntando a empresas inactivas y un mismo email apuntando a
-- multiples company_id (4 y 12, 5 y 13, etc.). El JWT terminaba escogiendo
-- el id mas alto (12) y los datos seed no aparecian en ese tenant.
--
-- FIX EN 3 PASOS (idempotente):
--   1) Cleanup: para cada email duplicado conservar SOLO una fila activa
--      (la mas reciente con deleted_at IS NULL). El resto queda soft-deleted
--      con NOW(). NO se elimina fisicamente para preservar audit_logs y FKs.
--   2) Cleanup roles huerfanos: borrar users_roles que apuntan a users con
--      deleted_at IS NOT NULL (relaciones muertas).
--   3) Crear UNIQUE INDEX PARCIAL sobre (lower(email)) WHERE deleted_at IS
--      NULL. Asi futuros INSERTs con email duplicado fallaran y los seeds
--      con ON CONFLICT DO NOTHING/WHERE NOT EXISTS funcionaran correctamente.
--
-- Tambien crea UNIQUE INDEX PARCIAL sobre username con la misma logica.
--
-- Re-ejecutable: si el index ya existe, no falla. Si no hay duplicados, el
-- DELETE/UPDATE no afecta filas.

-- =====================================================================
-- PASO 1 — Soft-delete de users duplicados por email (conservar el mejor)
-- =====================================================================
DO $$
DECLARE
    v_dup RECORD;
    v_keep_id BIGINT;
BEGIN
    FOR v_dup IN
        SELECT lower(email) AS email_norm, COUNT(*) AS cnt
          FROM users
         WHERE deleted_at IS NULL
         GROUP BY lower(email)
        HAVING COUNT(*) > 1
    LOOP
        -- Estrategia de "mejor": mas reciente (id DESC) que apunte a una
        -- empresa ACTIVE (si hay) o la mas reciente sin filtro.
        SELECT u.id INTO v_keep_id
          FROM users u
          LEFT JOIN companies c ON c.id = u.company_id
         WHERE lower(u.email) = v_dup.email_norm
           AND u.deleted_at IS NULL
         ORDER BY (CASE WHEN c.deleted_at IS NULL AND c.status='ACTIVE' THEN 0 ELSE 1 END) ASC,
                  u.id DESC
         LIMIT 1;

        IF v_keep_id IS NOT NULL THEN
            UPDATE users
               SET deleted_at = NOW(), updated_at = NOW()
             WHERE lower(email) = v_dup.email_norm
               AND deleted_at IS NULL
               AND id <> v_keep_id;
            RAISE NOTICE 'V9-ZZZT: email % conservado id=% (% duplicados eliminados)',
                v_dup.email_norm, v_keep_id, v_dup.cnt - 1;
        END IF;
    END LOOP;
END $$;

-- =====================================================================
-- PASO 2 — Limpiar users_roles huerfanos (apuntan a users soft-deleted)
-- =====================================================================
DELETE FROM users_roles
 WHERE user_id IN (SELECT id FROM users WHERE deleted_at IS NOT NULL);

-- =====================================================================
-- PASO 3 — UNIQUE INDEX parcial sobre email + username (case insensitive)
-- =====================================================================
-- Estos indices garantizan que futuras inserciones con email duplicado
-- (entre filas activas) fallen con UNIQUE violation. ON CONFLICT DO NOTHING
-- en la seed V9-ZZC funcionara correctamente.

DROP INDEX IF EXISTS uk_users_email_active;
CREATE UNIQUE INDEX uk_users_email_active
    ON users (lower(email))
 WHERE deleted_at IS NULL;

DROP INDEX IF EXISTS uk_users_username_active;
CREATE UNIQUE INDEX uk_users_username_active
    ON users (lower(username))
 WHERE deleted_at IS NULL;

-- =====================================================================
-- PASO 4 — Limpieza de companies duplicadas por NIT
-- =====================================================================
-- Mismo bug en companies: V9-ZZC re-creaba empresas QA (NIT 9001NNNNNN)
-- en cada arranque porque el filtro de cleanup NO las excluia. Result:
-- multiples filas activas con el mismo NIT. Conservamos la mas reciente
-- (id mas alto) que apunte a las QA originales y soft-deleteamos el resto.
DO $$
DECLARE
    v_dup RECORD;
    v_keep_id BIGINT;
BEGIN
    FOR v_dup IN
        SELECT nit, COUNT(*) AS cnt
          FROM companies
         WHERE deleted_at IS NULL
         GROUP BY nit
        HAVING COUNT(*) > 1
    LOOP
        SELECT id INTO v_keep_id FROM companies
         WHERE nit = v_dup.nit AND deleted_at IS NULL
         ORDER BY id DESC LIMIT 1;
        UPDATE companies
           SET deleted_at = NOW(), status = 'INACTIVE', updated_at = NOW()
         WHERE nit = v_dup.nit AND deleted_at IS NULL AND id <> v_keep_id;
        RAISE NOTICE 'V9-ZZZT: company NIT % conservada id=% (% duplicadas eliminadas)',
            v_dup.nit, v_keep_id, v_dup.cnt - 1;
    END LOOP;
END $$;

-- =====================================================================
-- PASO 5 — Reasignar users activos cuyo company_id apunta a empresa
-- soft-deleted, pero existe otra empresa ACTIVE con el mismo NIT
-- (caso del bug: user.company_id=4 deleted -> debe pasar a id=12 active).
-- =====================================================================
DO $$
DECLARE
    v_user RECORD;
    v_active_co_id BIGINT;
BEGIN
    FOR v_user IN
        SELECT u.id, u.email, u.company_id, c_old.nit AS old_nit
          FROM users u
          JOIN companies c_old ON c_old.id = u.company_id
         WHERE u.deleted_at IS NULL
           AND c_old.deleted_at IS NOT NULL
    LOOP
        SELECT id INTO v_active_co_id FROM companies
         WHERE nit = v_user.old_nit AND deleted_at IS NULL AND status='ACTIVE'
         ORDER BY id DESC LIMIT 1;
        IF v_active_co_id IS NOT NULL AND v_active_co_id <> v_user.company_id THEN
            UPDATE users SET company_id = v_active_co_id, updated_at = NOW()
             WHERE id = v_user.id;
            RAISE NOTICE 'V9-ZZZT: user % reasignado de company_id=% a %',
                v_user.email, v_user.company_id, v_active_co_id;
        END IF;
    END LOOP;
END $$;

-- =====================================================================
-- PASO 6 — UNIQUE INDEX parcial sobre companies.nit (un NIT activo unico)
-- =====================================================================
DROP INDEX IF EXISTS uk_companies_nit_active;
CREATE UNIQUE INDEX uk_companies_nit_active
    ON companies (nit)
 WHERE deleted_at IS NULL;

-- =====================================================================
-- PASO 7 — Soft-delete users con sufijo "tenantN" creados por V9-Z3
-- viejo. Ahora V9-Z3 solo crea para las 3 empresas legacy. Los emails
-- admin@tenant4.test, contador@tenant5.test, etc., son ruido de seeds
-- previos. Las empresas QA tienen sus admin@empresaN.test correctos.
-- =====================================================================
DELETE FROM users_roles
 WHERE user_id IN (
   SELECT id FROM users
    WHERE email ~ '^(admin|contador|auditor)@tenant[0-9]+\.test$'
      AND deleted_at IS NULL);

UPDATE users
   SET deleted_at = NOW(), updated_at = NOW()
 WHERE email ~ '^(admin|contador|auditor)@tenant[0-9]+\.test$'
   AND deleted_at IS NULL;
