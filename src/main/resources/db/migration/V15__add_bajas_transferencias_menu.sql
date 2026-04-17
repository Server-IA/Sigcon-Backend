-- F-HU-ACT-03-RF-01: Agregar menú de Bajas y Transferencias al módulo de Activos (module_id=3)

INSERT INTO menus (component, created_at, deleted_at, icon, "label", menu_order, "path", status, updated_at, module_id, parent_id, visible)
SELECT *
FROM (VALUES
    ('ACT_BAJAS_TRANSFERENCIAS', now(), NULL::timestamp, 'ri-arrow-left-right-line', 'Bajas y Transferencias', 6, 'bajas-transferencias', 'ACTIVE', now(), 3, NULL::bigint, TRUE)
) AS v(component, created_at, deleted_at, icon, "label", menu_order, "path", status, updated_at, module_id, parent_id, visible)
WHERE NOT EXISTS (
    SELECT 1 FROM menus m WHERE m.component = v.component AND m.module_id = v.module_id
);

-- Asignar permiso de menú al SUPERADMIN
INSERT INTO menu_permissions (created_at, deleted_at, updated_at, menu_id, role_id)
SELECT now(), NULL, now(), m.id, r.id
FROM menus m, roles r
WHERE m.component = 'ACT_BAJAS_TRANSFERENCIAS'
  AND r.name = 'SUPERADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM menu_permissions mp WHERE mp.menu_id = m.id AND mp.role_id = r.id
  );
