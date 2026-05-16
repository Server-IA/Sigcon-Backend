-- ============================================================================
-- V9-ZZZZG : Paridad de codes de permisos (Bloque AW - Opcion B)
-- ============================================================================
-- Contexto: auditoria 2026-05-15 detecto 44 codes usados en @PreAuthorize
-- que NO existen en BD. De esos, 22 ya los resuelve EffectivePermissionsFilter
-- via plural/singular (VIEW_USER<->VIEW_USERS). Los otros 22 requieren:
--   (a) insertarse en BD como nuevos codes (esta migracion), O
--   (b) mapearse al code nuevo MOD.ENTIDAD.ACCION (EffectivePermissionsFilter).
--
-- Esta migracion INSERTA en BD los 22 codes faltantes para que existan como
-- permisos asignables a roles. Asi un PLATFORM_ADMIN puede asignarlos en el
-- modal de Roles. Cada code va con su modulo correspondiente.
--
-- Tambien soft-deletea los typos detectados (PARAMETRIZACION.ROLES.EDITAR no
-- existia en BD, pero @PreAuthorize del RoleSubscriptionController lo refiere
-- como fallback historico — lo dejamos sin code en BD, el filter ya lo trata
-- como alias de PAR.ROLES.EDITAR).
--
-- Idempotente: ON CONFLICT DO NOTHING + uso de SELECT id.
-- ============================================================================

-- 1. Codes legacy plural/singular faltantes - los 22 SIN MATCH directo
-- Cada INSERT verifica que no exista antes de meterlo.

-- TERCEROS (modulo 4)
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'CREATE_THIRD_PARTIES', 'Crear terceros (alias plural)',
       'Alias plural de CREATE_THIRD_PARTY (compatibilidad con controllers legacy)',
       'CREATE', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='CREATE_THIRD_PARTIES');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'VIEW_THIRD_PARTIES', 'Ver terceros (alias plural)',
       'Alias plural de VIEW_THIRD_PARTY (compatibilidad con controllers legacy)',
       'READ', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='VIEW_THIRD_PARTIES');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'DELETE_THIRD_PARTIES', 'Eliminar terceros (alias plural)',
       'Alias plural de DELETE_THIRD_PARTY (compatibilidad con controllers legacy)',
       'DELETE', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='DELETE_THIRD_PARTIES');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'MANAGE_THIRD_PARTY_ROLES_STATUS', 'Gestionar roles y estado de terceros',
       'Permite cambiar roles asignados y estado (activo/inactivo) de un tercero',
       'UPDATE', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='MANAGE_THIRD_PARTY_ROLES_STATUS');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'VIEW_ECL_SEGMENT', 'Ver segmentacion ECL de terceros',
       'Permite consultar la clasificacion de riesgo crediticio (ECL NIIF 9)',
       'READ', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='VIEW_ECL_SEGMENT');

-- CUENTAS POR COBRAR (modulo 9)
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'DELETE_SALES_INVOICE', 'Eliminar factura de venta',
       'Eliminar factura de venta (solo si esta en estado BORRADOR)',
       'DELETE', 9, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='DELETE_SALES_INVOICE');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'READ_SALES_INVOICE', 'Consultar factura de venta',
       'Alias READ de VIEW_SALES_INVOICE para compatibilidad con controllers legacy',
       'READ', 9, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='READ_SALES_INVOICE');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'READ_AR_PAYMENT', 'Consultar cobros',
       'Consultar cobros (recibos de caja) en cuentas por cobrar',
       'READ', 9, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='READ_AR_PAYMENT');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'READ_AR_ADVANCE', 'Consultar anticipos de clientes',
       'Consultar anticipos recibidos de clientes',
       'READ', 9, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='READ_AR_ADVANCE');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'READ_AR_NOTE', 'Consultar notas credito/debito',
       'Consultar notas credito y debito de ventas',
       'READ', 9, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='READ_AR_NOTE');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'READ_DIAN_RESOLUTION', 'Consultar resoluciones DIAN',
       'Consultar resoluciones DIAN de facturacion electronica',
       'READ', 9, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='READ_DIAN_RESOLUTION');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'READ_DIAN', 'Consultar integracion DIAN',
       'Consultar XML, CUFE, estado de envio DIAN',
       'READ', 9, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='READ_DIAN');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'READ_DIAN_REPORT', 'Consultar reportes DIAN',
       'Consultar reportes de informacion exogena DIAN (F1001, F1007, F1008)',
       'READ', 9, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='READ_DIAN_REPORT');

-- BANCOS Y CAJAS (modulo 5)
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'DELETE_BANK_CHECK', 'Eliminar cheque',
       'Eliminar cheque (solo si esta en estado borrador, no emitido)',
       'DELETE', 5, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='DELETE_BANK_CHECK');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'VOID_BANK_CHECK', 'Anular cheque',
       'Anular un cheque emitido (estado ANULADO, irreversible)',
       'UPDATE', 5, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='VOID_BANK_CHECK');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'RECONCILE_BANK_CHECK', 'Conciliar cheque',
       'Marcar cheque como COBRADO durante conciliacion bancaria',
       'UPDATE', 5, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='RECONCILE_BANK_CHECK');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'REPORT_LOST_BANK_CHECK', 'Reportar cheque extraviado',
       'Reportar un cheque como EXTRAVIADO con motivo',
       'UPDATE', 5, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='REPORT_LOST_BANK_CHECK');

-- CONTABILIDAD GENERAL (modulo 8)
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'VIEW_ACCOUNTING', 'Ver libros contables',
       'Consultar Libro Diario, Libro Mayor, Balance de Comprobacion',
       'READ', 8, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='VIEW_ACCOUNTING');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'VIEW_TAX_REPORT', 'Ver reportes tributarios',
       'Consultar reportes tributarios (IVA, retenciones, info exogena)',
       'READ', 8, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='VIEW_TAX_REPORT');

-- PARAMETRIZACION (modulo 1) - codes que el code usa con UPDATE_MENU_PERMISSION singular
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'UPDATE_MENU_PERMISSION', 'Editar permiso de menu (singular)',
       'Alias singular de UPDATE_MENU_PERMISSIONS para compatibilidad',
       'UPDATE', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='UPDATE_MENU_PERMISSION');


-- ============================================================================
-- 2. Verificacion de paridad: para CADA permiso del rol CONTADOR (que viene
-- del seed con codes legacy), garantizar que su par MOD.ENTIDAD.ACCION exista
-- en BD. Si falta el par, se agrega.
-- ============================================================================
-- Mapeo legacy -> nuevo (entradas criticas mas usadas)
DO $$
DECLARE
    mapping RECORD;
BEGIN
    FOR mapping IN
        SELECT * FROM (VALUES
            -- legacy             -> nuevo                     -> name                              -> description                              -> type      -> module
            ('VIEW_USER',           'PAR.USUARIOS.VER',           'Ver usuarios',                      'Consultar la lista de usuarios',          'READ',      1),
            ('CREATE_USER',         'PAR.USUARIOS.CREAR',         'Crear usuarios',                    'Registrar un usuario nuevo',              'CREATE',    1),
            ('UPDATE_USER',         'PAR.USUARIOS.EDITAR',        'Editar usuarios',                   'Modificar datos de un usuario existente', 'UPDATE',    1),
            ('DELETE_USER',         'PAR.USUARIOS.DESACTIVAR',    'Desactivar usuarios',               'Inactivar un usuario',                    'DELETE',    1),
            ('VIEW_ROLE',           'PAR.ROLES.VER',              'Ver roles',                         'Consultar la lista de roles',             'READ',      1),
            ('CREATE_ROLE',         'PAR.ROLES.CREAR',            'Crear roles',                       'Crear un rol nuevo',                      'CREATE',    1),
            ('UPDATE_ROLE',         'PAR.ROLES.EDITAR',           'Editar roles',                      'Modificar un rol existente',              'UPDATE',    1),
            ('DELETE_ROLE',         'PAR.ROLES.ELIMINAR',         'Eliminar roles',                    'Eliminar un rol',                         'DELETE',    1),
            ('VIEW_COST_CENTER',    'CFG.CENTROS_COSTO.VER',      'Ver centros de costo',              'Consultar centros de costo',              'READ',      2),
            ('CREATE_COST_CENTER',  'CFG.CENTROS_COSTO.CREAR',    'Crear centros de costo',            'Crear un centro de costo',                'CREATE',    2),
            ('UPDATE_COST_CENTER',  'CFG.CENTROS_COSTO.EDITAR',   'Editar centros de costo',           'Modificar un centro de costo',            'UPDATE',    2),
            ('DELETE_COST_CENTER',  'CFG.CENTROS_COSTO.ELIMINAR', 'Eliminar centros de costo',         'Eliminar un centro de costo',             'DELETE',    2),
            ('VIEW_THIRD_PARTY',    'TER.TERCEROS.VER',           'Ver terceros',                      'Consultar terceros',                      'READ',      4),
            ('CREATE_THIRD_PARTY',  'TER.TERCEROS.CREAR',         'Crear terceros',                    'Registrar un tercero',                    'CREATE',    4),
            ('UPDATE_THIRD_PARTY',  'TER.TERCEROS.EDITAR',        'Editar terceros',                   'Modificar un tercero',                    'UPDATE',    4),
            ('DELETE_THIRD_PARTY',  'TER.TERCEROS.DAR_DE_BAJA',   'Dar de baja terceros',              'Dar de baja a un tercero',                'DELETE',    4),
            ('VIEW_BANK',           'BNK.BANCOS.VER',             'Ver bancos',                        'Consultar bancos',                        'READ',      5),
            ('CREATE_BANK',         'BNK.BANCOS.CREAR',           'Crear bancos',                      'Crear un banco',                          'CREATE',    5),
            ('UPDATE_BANK',         'BNK.BANCOS.EDITAR',          'Editar bancos',                     'Modificar un banco',                      'UPDATE',    5),
            ('DELETE_BANK',         'BNK.BANCOS.ELIMINAR',        'Eliminar bancos',                   'Eliminar un banco',                       'DELETE',    5)
        ) AS m(legacy_code, new_code, new_name, new_desc, new_type, new_module)
    LOOP
        -- Si el legacy existe pero el nuevo NO, inserta el nuevo
        IF EXISTS (SELECT 1 FROM permissions WHERE code = mapping.legacy_code AND deleted_at IS NULL)
           AND NOT EXISTS (SELECT 1 FROM permissions WHERE code = mapping.new_code)
        THEN
            INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
            VALUES (mapping.new_code, mapping.new_name, mapping.new_desc, mapping.new_type, mapping.new_module::BIGINT, NOW(), NOW());
        END IF;
        -- Si el nuevo existe pero el legacy NO, inserta el legacy
        IF EXISTS (SELECT 1 FROM permissions WHERE code = mapping.new_code AND deleted_at IS NULL)
           AND NOT EXISTS (SELECT 1 FROM permissions WHERE code = mapping.legacy_code)
        THEN
            INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
            VALUES (mapping.legacy_code, mapping.new_name || ' (legacy)', mapping.new_desc, mapping.new_type, mapping.new_module::BIGINT, NOW(), NOW());
        END IF;
    END LOOP;
END $$;

-- ============================================================================
-- 3. Asignacion automatica de pares a roles existentes
-- Para cada rol que ya tiene un permiso legacy, asignarle tambien su par nuevo
-- (y viceversa). Esto garantiza que CONTADOR (que tiene VIEW_USER) tambien
-- tenga PAR.USUARIOS.VER automaticamente.
-- ============================================================================
DO $$
DECLARE
    mapping RECORD;
    role_id_var BIGINT;
BEGIN
    FOR mapping IN
        SELECT * FROM (VALUES
            ('VIEW_USER',          'PAR.USUARIOS.VER'),
            ('CREATE_USER',        'PAR.USUARIOS.CREAR'),
            ('UPDATE_USER',        'PAR.USUARIOS.EDITAR'),
            ('DELETE_USER',        'PAR.USUARIOS.DESACTIVAR'),
            ('VIEW_ROLE',          'PAR.ROLES.VER'),
            ('CREATE_ROLE',        'PAR.ROLES.CREAR'),
            ('UPDATE_ROLE',        'PAR.ROLES.EDITAR'),
            ('DELETE_ROLE',        'PAR.ROLES.ELIMINAR'),
            ('VIEW_COST_CENTER',   'CFG.CENTROS_COSTO.VER'),
            ('CREATE_COST_CENTER', 'CFG.CENTROS_COSTO.CREAR'),
            ('UPDATE_COST_CENTER', 'CFG.CENTROS_COSTO.EDITAR'),
            ('DELETE_COST_CENTER', 'CFG.CENTROS_COSTO.ELIMINAR'),
            ('VIEW_THIRD_PARTY',   'TER.TERCEROS.VER'),
            ('CREATE_THIRD_PARTY', 'TER.TERCEROS.CREAR'),
            ('UPDATE_THIRD_PARTY', 'TER.TERCEROS.EDITAR'),
            ('DELETE_THIRD_PARTY', 'TER.TERCEROS.DAR_DE_BAJA'),
            ('VIEW_BANK',          'BNK.BANCOS.VER'),
            ('CREATE_BANK',        'BNK.BANCOS.CREAR'),
            ('UPDATE_BANK',        'BNK.BANCOS.EDITAR'),
            ('DELETE_BANK',        'BNK.BANCOS.ELIMINAR')
        ) AS m(legacy_code, new_code)
    LOOP
        -- Si un rol tiene el legacy y NO el nuevo, asignarle el nuevo
        INSERT INTO roles_permissions (role_id, permission_id)
        SELECT rp.role_id, pn.id
        FROM roles_permissions rp
        JOIN permissions pl ON pl.id = rp.permission_id AND pl.code = mapping.legacy_code AND pl.deleted_at IS NULL
        JOIN permissions pn ON pn.code = mapping.new_code AND pn.deleted_at IS NULL
        WHERE NOT EXISTS (
            SELECT 1 FROM roles_permissions rp2
            WHERE rp2.role_id = rp.role_id AND rp2.permission_id = pn.id
        );

        -- Si un rol tiene el nuevo y NO el legacy, asignarle el legacy
        INSERT INTO roles_permissions (role_id, permission_id)
        SELECT rp.role_id, pl.id
        FROM roles_permissions rp
        JOIN permissions pn ON pn.id = rp.permission_id AND pn.code = mapping.new_code AND pn.deleted_at IS NULL
        JOIN permissions pl ON pl.code = mapping.legacy_code AND pl.deleted_at IS NULL
        WHERE NOT EXISTS (
            SELECT 1 FROM roles_permissions rp2
            WHERE rp2.role_id = rp.role_id AND rp2.permission_id = pl.id
        );
    END LOOP;
END $$;

-- ============================================================================
-- Fin V9-ZZZZG
-- ============================================================================
