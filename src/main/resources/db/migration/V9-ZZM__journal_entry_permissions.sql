-- =============================================================================
-- V9-ZZM__journal_entry_permissions.sql
--
-- HU-CG-03A E1 (Bloque X): el rol CONTADOR debe poder crear comprobantes
-- contables. Antes el endpoint POST /api/v1/journal-entries/store solo aceptaba
-- ROLE_ADMIN, lo que dejaba al contador sin permiso (HTTP 403).
--
-- Esta migracion:
--   1. Crea 5 permisos granulares JE: CREATE/UPDATE/DELETE/APPROVE/REVERSE
--   2. Asigna CREATE/UPDATE/APPROVE/REVERSE al rol CONTADOR
--   3. Asigna los 5 a ADMIN (incluyendo DELETE)
--
-- Idempotente: WHERE NOT EXISTS en INSERTs y ON CONFLICT en roles_permissions.
-- Schema real de permissions: (code, name, description, type, module_id, ...).
-- module_id de Contabilidad General = 8. type IN (CREATE,READ,UPDATE,DELETE).
-- Tabla relacion: roles_permissions(role_id, permission_id).
-- =============================================================================

-- 1. Crear permisos faltantes
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'CREATE_JOURNAL_ENTRY', 'Crear comprobante contable', 'Permite crear comprobantes contables en BORRADOR', 'CREATE', 8, NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='CREATE_JOURNAL_ENTRY' AND deleted_at IS NULL);

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'UPDATE_JOURNAL_ENTRY', 'Actualizar comprobante contable', 'Permite editar comprobantes en BORRADOR o crear correcciones', 'UPDATE', 8, NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='UPDATE_JOURNAL_ENTRY' AND deleted_at IS NULL);

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'DELETE_JOURNAL_ENTRY', 'Eliminar comprobante contable', 'Permite eliminar comprobantes en BORRADOR (solo ADMIN)', 'DELETE', 8, NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='DELETE_JOURNAL_ENTRY' AND deleted_at IS NULL);

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'APPROVE_JOURNAL_ENTRY', 'Aprobar/contabilizar comprobante', 'Permite contabilizar (DRAFT->POSTED)', 'UPDATE', 8, NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='APPROVE_JOURNAL_ENTRY' AND deleted_at IS NULL);

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'REVERSE_JOURNAL_ENTRY', 'Reversar comprobante contabilizado', 'Permite reversar (POSTED->REVERSED) generando JE inverso', 'UPDATE', 8, NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='REVERSE_JOURNAL_ENTRY' AND deleted_at IS NULL);

-- 2. Asignar a CONTADOR los de operacion habitual
DO $$
DECLARE
    v_role_contador BIGINT;
    v_perm_id BIGINT;
BEGIN
    SELECT id INTO v_role_contador FROM roles WHERE name='CONTADOR' AND deleted_at IS NULL LIMIT 1;
    IF v_role_contador IS NULL THEN
        RAISE NOTICE 'V9-ZZM: Rol CONTADOR no existe, saltando asignaciones.';
        RETURN;
    END IF;

    -- CONTADOR puede CREATE, UPDATE, APPROVE (contabilizar) y REVERSE comprobantes.
    -- NO puede DELETE (preservacion contable).
    FOR v_perm_id IN
        SELECT id FROM permissions
         WHERE code IN ('CREATE_JOURNAL_ENTRY','UPDATE_JOURNAL_ENTRY','APPROVE_JOURNAL_ENTRY','REVERSE_JOURNAL_ENTRY')
           AND deleted_at IS NULL
    LOOP
        INSERT INTO roles_permissions (role_id, permission_id)
        VALUES (v_role_contador, v_perm_id)
        ON CONFLICT DO NOTHING;
    END LOOP;

    RAISE NOTICE 'V9-ZZM: Permisos JE asignados al rol CONTADOR (id=%)', v_role_contador;
END $$;

-- 3. Asignar a ADMIN todos los permisos JE (incluyendo DELETE)
DO $$
DECLARE
    v_role_admin BIGINT;
    v_perm_id BIGINT;
BEGIN
    SELECT id INTO v_role_admin FROM roles WHERE name='ADMIN' AND deleted_at IS NULL LIMIT 1;
    IF v_role_admin IS NULL THEN
        RAISE NOTICE 'V9-ZZM: Rol ADMIN no existe, saltando asignaciones.';
        RETURN;
    END IF;

    FOR v_perm_id IN
        SELECT id FROM permissions
         WHERE code IN ('CREATE_JOURNAL_ENTRY','UPDATE_JOURNAL_ENTRY','DELETE_JOURNAL_ENTRY',
                        'APPROVE_JOURNAL_ENTRY','REVERSE_JOURNAL_ENTRY')
           AND deleted_at IS NULL
    LOOP
        INSERT INTO roles_permissions (role_id, permission_id)
        VALUES (v_role_admin, v_perm_id)
        ON CONFLICT DO NOTHING;
    END LOOP;

    RAISE NOTICE 'V9-ZZM: Permisos JE asignados al rol ADMIN (id=%)', v_role_admin;
END $$;
