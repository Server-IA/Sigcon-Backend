-- Agregar menús de Países y Municipios al módulo de Parametrización (module_id=1)
-- Resuelve errores QA: CP-FUNC-PA-RF-51, CP-FUNC-PA-RF-52, CP-FUNC-PA-RF-55, CP-FUNC-PA-RF-56

INSERT INTO menus (component, created_at, deleted_at, icon, "label", menu_order, "path", status, updated_at, module_id, parent_id, visible)
SELECT *
FROM (
    VALUES
    ('PAISES', now(), NULL::timestamp, 'ri-global-line', 'Países', 9, 'paises', 'ACTIVE', now(), 1, NULL::bigint, TRUE),
    ('MUNICIPIOS', now(), NULL::timestamp, 'ri-map-2-line', 'Municipios', 10, 'municipios', 'ACTIVE', now(), 1, NULL::bigint, TRUE),
    ('LOCALIDADES', now(), NULL::timestamp, 'ri-map-pin-line', 'Localidades', 11, 'localidades', 'ACTIVE', now(), 1, NULL::bigint, TRUE)
) AS v(component, created_at, deleted_at, icon, "label", menu_order, "path", status, updated_at, module_id, parent_id, visible)
WHERE NOT EXISTS (
    SELECT 1 FROM menus m WHERE m.component = v.component AND m.module_id = v.module_id AND m.path = v.path
);

-- Asignar permisos de menú al SUPERADMIN
INSERT INTO menu_permissions (created_at, deleted_at, updated_at, menu_id, role_id)
SELECT now(), NULL, now(), m.id, r.id
FROM menus m, roles r
WHERE m.component IN ('PAISES', 'MUNICIPIOS', 'LOCALIDADES')
  AND r.name = 'SUPERADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM menu_permissions mp WHERE mp.menu_id = m.id AND mp.role_id = r.id
  );
