-- =====================================================================
-- HU-PA-13 + HU-PA-14 (Bloque PA Bugs 31-43, 2026-05-09)
-- =====================================================================
-- La tabla `temporary_permissions` ya la crea Hibernate ddl-auto desde
-- la entidad TemporaryPermission. Esta migracion solo se encarga de:
--
--   * Registrar los 3 permisos atomicos requeridos por el controller
--     (PAR.PERMISOS_TEMPORALES.ASIGNAR / .REVOCAR / .VER). El prefijo
--     `PERM_` lo agrega User.java como authority en runtime.
--   * Asignarlos a los roles ADMIN_EMPRESA y ADMIN. AUDITOR recibe solo VER.
--   * Sembrar el menu TEMPORARY_PERMISSIONS para que aparezca en la UI.
--
-- Idempotente. La columna code en permissions NO tiene UNIQUE, por eso
-- todos los INSERT usan WHERE NOT EXISTS.
-- =====================================================================

DO $$
DECLARE
    v_pa_module_id BIGINT;
    v_perm_assign_id BIGINT;
    v_perm_revoke_id BIGINT;
    v_perm_view_id BIGINT;
    v_role_id BIGINT;
BEGIN
    SELECT id INTO v_pa_module_id FROM modules
     WHERE (LOWER(name) IN ('parametrizacion','parametrizacin','parametrización') OR id = 1)
       AND deleted_at IS NULL
     ORDER BY id LIMIT 1;
    IF v_pa_module_id IS NULL THEN
        RAISE NOTICE 'Modulo Parametrizacion no existe, omitiendo seed permisos temporales';
        RETURN;
    END IF;

    INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
    SELECT 'PAR.PERMISOS_TEMPORALES.ASIGNAR',
           'Asignar permisos temporales',
           'Permite asignar permisos temporales a otros usuarios.',
           'CREATE', v_pa_module_id, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='PAR.PERMISOS_TEMPORALES.ASIGNAR' AND deleted_at IS NULL);

    INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
    SELECT 'PAR.PERMISOS_TEMPORALES.REVOCAR',
           'Revocar permisos temporales',
           'Permite revocar permisos temporales antes de su vencimiento.',
           'DELETE', v_pa_module_id, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='PAR.PERMISOS_TEMPORALES.REVOCAR' AND deleted_at IS NULL);

    INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
    SELECT 'PAR.PERMISOS_TEMPORALES.VER',
           'Ver permisos temporales',
           'Permite consultar el listado de permisos temporales asignados.',
           'READ', v_pa_module_id, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='PAR.PERMISOS_TEMPORALES.VER' AND deleted_at IS NULL);

    SELECT id INTO v_perm_assign_id FROM permissions WHERE code='PAR.PERMISOS_TEMPORALES.ASIGNAR' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_perm_revoke_id FROM permissions WHERE code='PAR.PERMISOS_TEMPORALES.REVOCAR' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_perm_view_id   FROM permissions WHERE code='PAR.PERMISOS_TEMPORALES.VER' AND deleted_at IS NULL LIMIT 1;

    -- Asignar los 3 a TODOS los roles ADMIN_EMPRESA per-tenant + ADMIN global
    FOR v_role_id IN
        SELECT id FROM roles
        WHERE deleted_at IS NULL
          AND UPPER(name) IN ('ADMIN_EMPRESA','ADMIN')
    LOOP
        INSERT INTO roles_permissions (role_id, permission_id)
        SELECT v_role_id, v_perm_assign_id
        WHERE NOT EXISTS (SELECT 1 FROM roles_permissions rp WHERE rp.role_id=v_role_id AND rp.permission_id=v_perm_assign_id);

        INSERT INTO roles_permissions (role_id, permission_id)
        SELECT v_role_id, v_perm_revoke_id
        WHERE NOT EXISTS (SELECT 1 FROM roles_permissions rp WHERE rp.role_id=v_role_id AND rp.permission_id=v_perm_revoke_id);

        INSERT INTO roles_permissions (role_id, permission_id)
        SELECT v_role_id, v_perm_view_id
        WHERE NOT EXISTS (SELECT 1 FROM roles_permissions rp WHERE rp.role_id=v_role_id AND rp.permission_id=v_perm_view_id);
    END LOOP;

    -- AUDITOR solo VER
    FOR v_role_id IN
        SELECT id FROM roles WHERE deleted_at IS NULL AND UPPER(name) = 'AUDITOR'
    LOOP
        INSERT INTO roles_permissions (role_id, permission_id)
        SELECT v_role_id, v_perm_view_id
        WHERE NOT EXISTS (SELECT 1 FROM roles_permissions rp WHERE rp.role_id=v_role_id AND rp.permission_id=v_perm_view_id);
    END LOOP;

    -- Menu en Parametrizacion (visible en sidebar/avatar)
    INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
    SELECT 'Permisos Temporales', 'ri-time-line', 'permisos-temporales', 60, v_pa_module_id, 'ACTIVE', 'TEMPORARY_PERMISSIONS', true, NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM menus WHERE component = 'TEMPORARY_PERMISSIONS' AND deleted_at IS NULL
    );
END $$;
