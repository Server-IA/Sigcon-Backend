-- ============================================================================
-- V9-ZZZP: Alineacion de permisos AP entre @PreAuthorize y BD (HU-AP RBAC)
-- ----------------------------------------------------------------------------
-- QA-BLOQUE-AY (2026-05-06): el reporte QA reportaba "no se pueden probar
-- permisos" en HU-AP-02 E5, AP-11 E3, AP-16 E3, AP-20 E3, AP-23 E3, AP-25 E8.
-- Causa raiz: los @PreAuthorize del codigo Java esperan permisos como
-- PERM_READ_INVOICE, PERM_UPDATE_INVOICE, PERM_CREATE_INVOICE_FC, etc., pero
-- la BD tiene los codes en formato espanol (AP.FACTURAS_COMPRA.VER) o ingles
-- alterno (VIEW_AP_INVOICE, CREATE_SUPPLIER_INVOICE). Resultado: cualquier rol
-- distinto a ADMIN_EMPRESA recibia 403 al intentar consultar/operar facturas
-- aunque el rol funcionalmente cubria la operacion.
--
-- Esta migracion:
--   1) Crea los codes que pide el codigo Java pero faltan en BD.
--   2) Los asigna a CONTADOR (operar AP), AUDITOR (solo lectura),
--      AUXILIAR_CONTABLE (operacion basica) y ADMIN_EMPRESA (todo).
--
-- Idempotente con NOT EXISTS para safe re-run.
-- ============================================================================

-- 0) Limpiar runs previos: si un run anterior creo UPDATE_INVOICE/READ_INVOICE/etc.
-- con un name que colisiona con permisos pre-existentes (V9-K), renombrar.
-- Esto previene fallos del re-run de V9-K que hace UPDATE name='Actualizar factura de compra'.
UPDATE permissions SET name = 'Actualizar factura de compra (alias INVOICE)'
 WHERE code = 'UPDATE_INVOICE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar facturas de compra (alias INVOICE)'
 WHERE code = 'READ_INVOICE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar/Anular factura de compra (alias INVOICE)'
 WHERE code = 'DELETE_INVOICE' AND deleted_at IS NULL;

-- 1) Crear permisos faltantes que el codigo Java referencia
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT v.code, v.name, v.description, v.type, 7 AS module_id, NOW(), NOW()
FROM (VALUES
    -- Facturas de compra
    ('READ_INVOICE',                'Consultar facturas (AP) - alias READ_INVOICE',     'Consultar y listar facturas de compra (HU-AP-01/02/06/24)', 'READ'),
    ('READ_AP_INVOICE',             'Consultar factura AP - READ_AP_INVOICE',           'Consultar factura de compra individual',                    'READ'),
    ('CREATE_INVOICE_FC',           'Crear factura de compra - CREATE_INVOICE_FC',      'Crear factura de compra (HU-AP-01)',                        'CREATE'),
    ('UPDATE_INVOICE',              'Editar facturas (AP) - alias UPDATE_INVOICE',      'Editar factura de compra (HU-AP-02)',                       'UPDATE'),
    ('UPDATE_AP_INVOICE',           'Editar factura AP - alias UPDATE_AP_INVOICE',      'Editar factura de compra (alias)',                          'UPDATE'),
    ('DELETE_INVOICE',              'Anular facturas (AP) - alias DELETE_INVOICE',      'Anular factura de compra (HU-AP-25)',                       'DELETE'),
    ('DELETE_AP_INVOICE',           'Anular factura AP - alias DELETE_AP_INVOICE',      'Anular/eliminar factura de compra (alias)',                 'DELETE'),

    -- Pagos AP
    ('READ_AP_PAYMENT',             'Consultar pagos a proveedores',                    'Consultar y listar pagos AP (HU-AP-04/07)',                 'READ'),

    -- Anticipos AP
    ('READ_AP_ADVANCE',             'Consultar anticipos a proveedores',                'Consultar y listar anticipos AP (HU-AP-05)',                'READ'),

    -- Notas AP
    ('READ_AP_NOTE',                'Consultar notas credito/debito AP',                'Consultar notas credito/debito AP (HU-AP-09)',              'READ'),

    -- Reportes AP
    ('READ_AP_REPORT',              'Consultar reportes AP',                            'Consultar reportes de cuentas por pagar (HU-AP-11/20)',     'READ'),

    -- Ordenes de Compra
    ('READ_PURCHASE_ORDER',         'Consultar ordenes de compra',                      'Consultar y listar OCs (HU-AP-15/16)',                      'READ'),
    ('UPDATE_PURCHASE_ORDER',       'Actualizar orden de compra',                       'Editar OC (HU-AP-16)',                                      'UPDATE'),
    ('DELETE_PURCHASE_ORDER',       'Eliminar orden de compra',                         'Eliminar OC en estado DRAFT',                               'DELETE'),

    -- Recepciones
    ('READ_GOODS_RECEIPT',          'Consultar recepciones de bienes',                  'Consultar y listar recepciones (HU-AP-18/19)',              'READ'),
    ('UPDATE_GOODS_RECEIPT',        'Actualizar recepcion de bienes',                   'Vincular factura, rechazar o devolver (HU-AP-19/21)',       'UPDATE')
) AS v(code, name, description, type)
WHERE NOT EXISTS (
    SELECT 1 FROM permissions p WHERE p.code = v.code AND p.deleted_at IS NULL
);

-- 2) Asignar a roles
-- ADMIN_EMPRESA: TODOS los permisos AP (operacion completa).
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN_EMPRESA'
  AND r.deleted_at IS NULL
  AND p.deleted_at IS NULL
  AND p.code IN (
      'READ_INVOICE','READ_AP_INVOICE','CREATE_INVOICE_FC','UPDATE_INVOICE','UPDATE_AP_INVOICE',
      'DELETE_INVOICE','DELETE_AP_INVOICE','READ_AP_PAYMENT','READ_AP_ADVANCE','READ_AP_NOTE',
      'READ_AP_REPORT','READ_PURCHASE_ORDER','UPDATE_PURCHASE_ORDER','DELETE_PURCHASE_ORDER',
      'READ_GOODS_RECEIPT','UPDATE_GOODS_RECEIPT'
  )
  AND NOT EXISTS (
      SELECT 1 FROM roles_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- CONTADOR: operacion completa AP (HU-AP-02 E5 dice CONTADOR puede operar; AUDITOR solo ver)
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'CONTADOR'
  AND r.deleted_at IS NULL
  AND p.deleted_at IS NULL
  AND p.code IN (
      'READ_INVOICE','READ_AP_INVOICE','CREATE_INVOICE_FC','UPDATE_INVOICE','UPDATE_AP_INVOICE',
      'DELETE_INVOICE','DELETE_AP_INVOICE','READ_AP_PAYMENT','READ_AP_ADVANCE','READ_AP_NOTE',
      'READ_AP_REPORT','READ_PURCHASE_ORDER','UPDATE_PURCHASE_ORDER',
      'READ_GOODS_RECEIPT','UPDATE_GOODS_RECEIPT'
  )
  AND NOT EXISTS (
      SELECT 1 FROM roles_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- AUDITOR: SOLO lectura (cumple HU-AP-11 E3 que el auditor PUEDE consultar reportes basicos
-- pero no avanzados. Aqui le damos READ a todo AP. El control de "reportes avanzados" se hace
-- en el endpoint usando ROLE_ADMIN_EMPRESA bypass o permiso especifico que el auditor no tiene).
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'AUDITOR'
  AND r.deleted_at IS NULL
  AND p.deleted_at IS NULL
  AND p.code IN (
      'READ_INVOICE','READ_AP_INVOICE','READ_AP_PAYMENT','READ_AP_ADVANCE','READ_AP_NOTE',
      'READ_AP_REPORT','READ_PURCHASE_ORDER','READ_GOODS_RECEIPT'
  )
  AND NOT EXISTS (
      SELECT 1 FROM roles_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- AUXILIAR_CONTABLE: lectura + operacion basica (no editar/eliminar facturas POSTED)
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'AUXILIAR_CONTABLE'
  AND r.deleted_at IS NULL
  AND p.deleted_at IS NULL
  AND p.code IN (
      'READ_INVOICE','READ_AP_INVOICE','CREATE_INVOICE_FC','UPDATE_INVOICE','UPDATE_AP_INVOICE',
      'READ_AP_PAYMENT','READ_AP_ADVANCE','READ_AP_NOTE','READ_AP_REPORT',
      'READ_PURCHASE_ORDER','UPDATE_PURCHASE_ORDER',
      'READ_GOODS_RECEIPT','UPDATE_GOODS_RECEIPT'
  )
  AND NOT EXISTS (
      SELECT 1 FROM roles_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 3) Mensaje informativo
DO $$
DECLARE c BIGINT;
BEGIN
    SELECT COUNT(*) INTO c FROM permissions WHERE code IN (
        'READ_INVOICE','READ_AP_INVOICE','CREATE_INVOICE_FC','UPDATE_INVOICE','UPDATE_AP_INVOICE',
        'DELETE_INVOICE','DELETE_AP_INVOICE','READ_AP_PAYMENT','READ_AP_ADVANCE','READ_AP_NOTE',
        'READ_AP_REPORT','READ_PURCHASE_ORDER','UPDATE_PURCHASE_ORDER','DELETE_PURCHASE_ORDER',
        'READ_GOODS_RECEIPT','UPDATE_GOODS_RECEIPT'
    ) AND deleted_at IS NULL;
    RAISE NOTICE 'V9-ZZZP: % permisos AP activos en BD', c;
END $$;
