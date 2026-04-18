-- Paso 5: Agregar permisos granulares para empresas (PA-RF41-EF5, PA-RF42-EF3, PA-RF44-EF3)
-- Y permisos para países, municipios y localidades

INSERT INTO permissions (name, description, type, code, created_at, updated_at, module_id)
SELECT v.name, v.description, v.type, v.code, now(), now(), 1
FROM (VALUES
    ('Ver empresas', 'Permiso para ver empresas', 'READ', 'VIEW_COMPANY'),
    ('Crear empresas', 'Permiso para crear empresas', 'CREATE', 'CREATE_COMPANY'),
    ('Editar empresas', 'Permiso para editar empresas', 'UPDATE', 'UPDATE_COMPANY'),
    ('Eliminar empresas', 'Permiso para eliminar empresas', 'DELETE', 'DELETE_COMPANY'),
    ('Ver países', 'Permiso para ver países', 'READ', 'VIEW_COUNTRY'),
    ('Crear países', 'Permiso para crear países', 'CREATE', 'CREATE_COUNTRY'),
    ('Editar países', 'Permiso para editar países', 'UPDATE', 'UPDATE_COUNTRY'),
    ('Eliminar países', 'Permiso para eliminar países', 'DELETE', 'DELETE_COUNTRY'),
    ('Ver municipios', 'Permiso para ver municipios', 'READ', 'VIEW_MUNICIPALITY'),
    ('Crear municipios', 'Permiso para crear municipios', 'CREATE', 'CREATE_MUNICIPALITY'),
    ('Editar municipios', 'Permiso para editar municipios', 'UPDATE', 'UPDATE_MUNICIPALITY'),
    ('Eliminar municipios', 'Permiso para eliminar municipios', 'DELETE', 'DELETE_MUNICIPALITY')
) AS v(name, description, type, code)
WHERE NOT EXISTS (
    SELECT 1 FROM permissions p WHERE p.code = v.code AND p.deleted_at IS NULL
);

-- Asignar permisos al rol SUPERADMIN
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SUPERADMIN'
  AND p.code IN ('VIEW_COMPANY', 'CREATE_COMPANY', 'UPDATE_COMPANY', 'DELETE_COMPANY',
                  'VIEW_COUNTRY', 'CREATE_COUNTRY', 'UPDATE_COUNTRY', 'DELETE_COUNTRY',
                  'VIEW_MUNICIPALITY', 'CREATE_MUNICIPALITY', 'UPDATE_MUNICIPALITY', 'DELETE_MUNICIPALITY')
  AND p.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM roles_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
