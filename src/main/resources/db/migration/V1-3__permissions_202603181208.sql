INSERT INTO permissions (code, created_at, deleted_at, description, name, "type", updated_at, module_id)
SELECT *
FROM (
    VALUES
    ('VIEW_ROLES',now(),NULL::timestamp,'Permiso para ver roles','Obtener roles','READ',now(),1),
    ('CREATE_ROLE',now(),NULL::timestamp,'Permiso para crear roles','Crear roles','CREATE',now(),1),
    ('UPDATE_ROLE',now(),NULL::timestamp,'Permiso para actualizar roles','Actualizar roles','UPDATE',now(),1),
    ('DELETE_ROLE',now(),NULL::timestamp,'Permiso para eliminar roles','Eliminar roles','DELETE',now(),1),
    ('ASSIGN_ROLE',now(),NULL::timestamp,'Permiso para asignar roles','Asignar roles','CREATE',now(),1),
    ('VIEW_USERS',now(),NULL::timestamp,'Permiso para ver usuarios','Obtener usuarios','READ',now(),1),
    ('CREATE_USER',now(),NULL::timestamp,'Permiso para crear usuarios','Crear usuarios','CREATE',now(),1),
    ('UPDATE_USER',now(),NULL::timestamp,'Permiso para actualizar usuarios','Actualizar usuarios','UPDATE',now(),1),
    ('DELETE_USER',now(),NULL::timestamp,'Permiso para eliminar usuarios','Eliminar usuarios','DELETE',now(),1),
    ('CREATE_ACCOUNTING_ACCOUNT',now(),NULL::timestamp,'Permiso para crear cuentas contables','Crear cuentas contables','CREATE',now(),1),

    ('VIEW_ACCOUNTING_ACCOUNT',now(),NULL::timestamp,'Permiso para ver cuentas contables','Ver cuentas contables','READ',now(),1),
    ('UPDATE_ACCOUNTING_ACCOUNT',now(),NULL::timestamp,'Permiso para actualizar cuentas contables','Actualizar cuentas contables','UPDATE',now(),1),
    ('DELETE_ACCOUNTING_ACCOUNT',now(),NULL::timestamp,'Permiso para eliminar cuentas contables','Eliminar cuentas contables','DELETE',now(),1),
    ('CREATE_PERMISSION',now(),NULL::timestamp,'Permiso para crear permisos','Crear permisos','CREATE',now(),1),
    ('UPDATE_PERMISSION',now(),NULL::timestamp,'Permiso para actualizar permisos','Actualizar permisos','UPDATE',now(),1),
    ('VIEW_PERMISSIONS',now(),NULL::timestamp,'Permiso para ver permisos','Obtener permisos','READ',now(),1),
    ('ASSIGN_PERMISSION',now(),NULL::timestamp,'Permiso para asignar permisos','Asignar permisos','CREATE',now(),1),
    ('REMOVE_PERMISSION',now(),NULL::timestamp,'Permiso para eliminar permisos','Eliminar permisos','DELETE',now(),1),
    ('CREATE_CHART_OF_ACCOUNT',now(),NULL::timestamp,'Permiso para crear cuentas de contabilidad','Crear cuentas de contabilidad','CREATE',now(),1),
    ('VIEW_CHART_OF_ACCOUNT',now(),NULL::timestamp,'Permiso para ver cuentas de contabilidad','Ver cuentas de contabilidad','READ',now(),1),

    ('UPDATE_CHART_OF_ACCOUNT',now(),NULL::timestamp,'Permiso para actualizar cuentas de contabilidad','Actualizar cuentas de contabilidad','UPDATE',now(),1),
    ('DELETE_CHART_OF_ACCOUNT',now(),NULL::timestamp,'Permiso para eliminar cuentas de contabilidad','Eliminar cuentas de contabilidad','DELETE',now(),1),
    ('VIEW_MENUS',now(),NULL::timestamp,'Permiso para ver menús','Ver menús','READ',now(),1),
    ('CREATE_MENUS',now(),NULL::timestamp,'Permiso para crear menús','Crear menús','CREATE',now(),1),
    ('UPDATE_MENUS',now(),NULL::timestamp,'Permiso para actualizar menús','Actualizar menús','UPDATE',now(),1),
    ('DELETE_MENUS',now(),NULL::timestamp,'Permiso para eliminar menús','Eliminar menús','DELETE',now(),1),
    ('CREATE_PARAMETER',now(),NULL::timestamp,'Permiso para crear parámetros','Crear parámetros','CREATE',now(),1),
    ('VIEW_PARAMETER',now(),NULL::timestamp,'Permiso para ver parámetros','Ver parámetros','READ',now(),1),
    ('UPDATE_PARAMETER',now(),NULL::timestamp,'Permiso para actualizar parámetros','Actualizar parámetros','UPDATE',now(),1),
    ('DELETE_PARAMETER',now(),NULL::timestamp,'Permiso para eliminar parámetros','Eliminar parámetros','DELETE',now(),1),

    ('VIEW_MENU_PERMISSIONS',now(),NULL::timestamp,'Permiso para ver permisos de menús','Ver permisos de menús','READ',now(),1),
    ('CREATE_MENU_PERMISSIONS',now(),NULL::timestamp,'Permiso para crear permisos de menús','Crear permisos de menús','CREATE',now(),1),
    ('UPDATE_MENU_PERMISSIONS',now(),NULL::timestamp,'Permiso para actualizar permisos de menús','Actualizar permisos de menús','UPDATE',now(),1),
    ('DELETE_MENU_PERMISSIONS',now(),NULL::timestamp,'Permiso para eliminar permisos de menús','Eliminar permisos de menús','DELETE',now(),1),

    ('VIEW_COST_CENTERS',now(),NULL::timestamp,'Permiso para ver centros de costo','Ver centros de costo','READ',now(),2),
    ('CREATE_COST_CENTER',now(),NULL::timestamp,'Permiso para crear centros de costo','Crear centros de costo','CREATE',now(),2),
    ('UPDATE_COST_CENTER',now(),NULL::timestamp,'Permiso para actualizar centros de costo','Actualizar centros de costo','UPDATE',now(),2),
    ('DELETE_COST_CENTER',now(),NULL::timestamp,'Permiso para eliminar centros de costo','Eliminar centros de costo','DELETE',now(),2),

    ('VIEW_DEPRECIATION_RULE',now(),NULL::timestamp,'Permiso para ver reglas de depreciación','Ver reglas de depreciación','READ',now(),2),
    ('CREATE_DEPRECIATION_RULE',now(),NULL::timestamp,'Permiso para crear reglas de depreciación','Crear reglas de depreciación','CREATE',now(),2),
    ('UPDATE_DEPRECIATION_RULE',now(),NULL::timestamp,'Permiso para actualizar reglas de depreciación','Actualizar reglas de depreciación','UPDATE',now(),2),
    ('DELETE_DEPRECIATION_RULE',now(),NULL::timestamp,'Permiso para eliminar reglas de depreciación','Eliminar reglas de depreciación','DELETE',now(),2),

    ('VIEW_MODULES',now(),NULL::timestamp,'Permiso para ver módulos','Ver módulos','READ',now(),1),
    ('CREATE_MODULES',now(),NULL::timestamp,'Permiso para crear módulos','Crear módulos','CREATE',now(),1),
    ('UPDATE_MODULES',now(),NULL::timestamp,'Permiso para actualizar módulos','Actualizar módulos','UPDATE',now(),1),
    ('DELETE_MODULES',now(),NULL::timestamp,'Permiso para eliminar módulos','Eliminar módulos','DELETE',now(),1),

    ('VIEW_MODULES_MENU',now(),NULL::timestamp,'Permiso para ver menús de módulos','Ver menús de módulos','READ',now(),1),

    ('VIEW_EXCHANGE_RATE',now(),NULL::timestamp,'Permiso para visualizar tasas de cambio','Ver tasas de cambio','READ',now(),2),
    ('CREATE_EXCHANGE_RATE',now(),NULL::timestamp,'Permiso para crear tasas de cambio','Crear tasa de cambio','CREATE',now(),2),
    ('UPDATE_EXCHANGE_RATE',now(),NULL::timestamp,'Permiso para actualizar tasas de cambio','Actualizar tasa de cambio','UPDATE',now(),2),
    ('DELETE_EXCHANGE_RATE',now(),NULL::timestamp,'Permiso para eliminar tasas de cambio','Eliminar tasa de cambio','DELETE',now(),2),

    ('VIEW_CURRENCY_TYPE',now(),NULL::timestamp,'Permiso para ver tipos de moneda','Ver tipos de moneda','READ',now(),2),
    ('CREATE_CURRENCY_TYPE',now(),NULL::timestamp,'Permiso para crear tipos de moneda','Crear tipos de moneda','CREATE',now(),2),
    ('UPDATE_CURRENCY_TYPE',now(),NULL::timestamp,'Permiso para actualizar tipos de moneda','Actualizar tipos de moneda','UPDATE',now(),2),
    ('DELETE_CURRENCY_TYPE',now(),NULL::timestamp,'Permiso para eliminar tipos de moneda','Eliminar tipos de moneda','DELETE',now(),2)

) AS v(code, created_at, deleted_at, description, name, "type", updated_at, module_id)
WHERE NOT EXISTS (
    SELECT 1 
    FROM permissions p 
    WHERE p.code = v.code
);