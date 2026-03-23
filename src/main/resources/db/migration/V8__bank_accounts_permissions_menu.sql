-- Permisos para cuentas bancarias (módulo Bancos y Cajas, id=5)
INSERT INTO permissions (code, created_at, deleted_at, description, name, "type", updated_at, module_id)
SELECT *
FROM (
    VALUES
    ('PERM_CREATE_BANK_ACCOUNT', now(), NULL::timestamp, 'Permiso para crear cuentas bancarias', 'Crear cuentas bancarias', 'CREATE', now(), 5),
    ('PERM_VIEW_BANK_ACCOUNT', now(), NULL::timestamp, 'Permiso para ver cuentas bancarias', 'Ver cuentas bancarias', 'READ', now(), 5),
    ('PERM_UPDATE_BANK_ACCOUNT', now(), NULL::timestamp, 'Permiso para actualizar cuentas bancarias', 'Actualizar cuentas bancarias', 'UPDATE', now(), 5),
    ('PERM_DELETE_BANK_ACCOUNT', now(), NULL::timestamp, 'Permiso para eliminar cuentas bancarias', 'Eliminar cuentas bancarias', 'DELETE', now(), 5)
) AS v(code, created_at, deleted_at, description, name, "type", updated_at, module_id)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

-- Menú Cuentas Bancarias (módulo Bancos y Cajas, id=5)
INSERT INTO menus (component, created_at, deleted_at, icon, "label", menu_order, "path", status, updated_at, module_id, parent_id)
SELECT *
FROM (
    VALUES
    ('BANK_ACCOUNTS', now(), NULL::timestamp, 'ri-bank-card-line', 'Cuentas Bancarias', 3, 'bank-accounts', 'ACTIVE', now(), 5, NULL::bigint)
) AS v(component, created_at, deleted_at, icon, "label", menu_order, "path", status, updated_at, module_id, parent_id)
WHERE NOT EXISTS (SELECT 1 FROM menus m WHERE m.component = v.component AND m.module_id = v.module_id);
