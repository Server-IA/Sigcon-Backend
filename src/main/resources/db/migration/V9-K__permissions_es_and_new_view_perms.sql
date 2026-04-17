-- V9-K: localizacion al espaniol de los 114 permisos de V9-J
--       + 4 permisos nuevos VIEW_* para modulos CG/AU/INT/NOM
--       + asignacion a roles CONTADOR y AUDITOR.
--
-- Idempotente: UPDATE para renombrar (nunca cambia code), INSERTs con WHERE NOT EXISTS,
-- INSERTs de roles_permissions con ON CONFLICT DO NOTHING.

-- ============================================================================
-- 0. Hard-delete de permisos huerfanos que V9-J soft-deleted (eran los de V9-I
--    con doble prefix PERM_ buggeado). Sus nombres inflados ocupan el UNIQUE(name)
--    y bloquean los UPDATEs de este script.
-- ============================================================================
DELETE FROM roles_permissions WHERE permission_id IN (
   SELECT id FROM permissions WHERE deleted_at IS NOT NULL AND code LIKE 'PERM\_%' ESCAPE '\'
);
DELETE FROM permissions WHERE deleted_at IS NOT NULL AND code LIKE 'PERM\_%' ESCAPE '\';

-- Renombrar permisos legacy singulares (reinsertados por V14) para liberar nombres
-- que vamos a reusar en los plurales de V9-J/V9-K.
UPDATE permissions SET name = 'Ver tasas de cambio (legacy)'  WHERE code = 'VIEW_EXCHANGE_RATE'   AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear tasa de cambio (legacy)' WHERE code = 'CREATE_EXCHANGE_RATE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Editar tasa de cambio (legacy)' WHERE code = 'UPDATE_EXCHANGE_RATE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar tasa de cambio (legacy)' WHERE code = 'DELETE_EXCHANGE_RATE' AND deleted_at IS NULL;

-- ============================================================================
-- 1. Traduccion de nombres al espaniol (114 permisos de V9-J)
-- ============================================================================
UPDATE permissions SET name = 'Ajustar segmento ECL', description = 'Permiso para ajustar segmento ecl' WHERE code = 'ADJUST_ECL_SEGMENT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Aprobar orden de compra', description = 'Permiso para aprobar orden de compra' WHERE code = 'APPROVE_PURCHASE_ORDER' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Asignar cuenta contable a regla tributaria', description = 'Permiso para asignar cuenta contable a regla tributaria' WHERE code = 'ASSIGN_ACCOUNTING_ACCOUNT_TO_RULER_TAX' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Cargar masivamente tercero', description = 'Permiso para cargar masivamente tercero' WHERE code = 'BULK_STORE_THIRD_PARTY' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Calcular segmento ECL', description = 'Permiso para calcular segmento ecl' WHERE code = 'CALCULATE_ECL_SEGMENT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Cambiar estado de caja', description = 'Permiso para cambiar estado de caja' WHERE code = 'CHANGE_CASH_STATUS' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear anticipo a proveedor', description = 'Permiso para crear anticipo a proveedor' WHERE code = 'CREATE_AP_ADVANCE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear nota de proveedor', description = 'Permiso para crear nota de proveedor' WHERE code = 'CREATE_AP_NOTE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear pago a proveedor', description = 'Permiso para crear pago a proveedor' WHERE code = 'CREATE_AP_PAYMENT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear anticipo de cliente', description = 'Permiso para crear anticipo de cliente' WHERE code = 'CREATE_AR_ADVANCE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear nota de cliente', description = 'Permiso para crear nota de cliente' WHERE code = 'CREATE_AR_NOTE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear cobro', description = 'Permiso para crear cobro' WHERE code = 'CREATE_AR_PAYMENT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear activo fijo', description = 'Permiso para crear activo fijo' WHERE code = 'CREATE_ASSET' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear banco', description = 'Permiso para crear banco' WHERE code = 'CREATE_BANK' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear cuenta bancaria', description = 'Permiso para crear cuenta bancaria' WHERE code = 'CREATE_BANK_ACCOUNT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear sucursal bancaria', description = 'Permiso para crear sucursal bancaria' WHERE code = 'CREATE_BANK_BRANCH' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear cheque', description = 'Permiso para crear cheque' WHERE code = 'CREATE_BANK_CHECK' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear caja', description = 'Permiso para crear caja' WHERE code = 'CREATE_CASH' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear chequera', description = 'Permiso para crear chequera' WHERE code = 'CREATE_CHECKBOOK' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear datos comerciales', description = 'Permiso para crear datos comerciales' WHERE code = 'CREATE_COMMERCIAL_DATA' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear resolucion DIAN', description = 'Permiso para crear resolucion dian' WHERE code = 'CREATE_DIAN_RESOLUTION' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear XML DIAN', description = 'Permiso para crear xml dian' WHERE code = 'CREATE_DIAN_XML' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Registrar tasa de cambio nueva', description = 'Permiso para registrar tasa de cambio nueva' WHERE code = 'CREATE_EXCHANGE_RATES' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear recepcion de mercancia', description = 'Permiso para crear recepcion de mercancia' WHERE code = 'CREATE_GOODS_RECEIPT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear factura de compra', description = 'Permiso para crear factura de compra' WHERE code = 'CREATE_INVOICE_FC' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear orden de compra', description = 'Permiso para crear orden de compra' WHERE code = 'CREATE_PURCHASE_ORDER' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear plantillas de reporte', description = 'Permiso para crear plantillas de reporte' WHERE code = 'CREATE_REPORT_TEMPLATES' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear tipos de reporte', description = 'Permiso para crear tipos de reporte' WHERE code = 'CREATE_REPORT_TYPES' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear regla tributaria', description = 'Permiso para crear regla tributaria' WHERE code = 'CREATE_RULER_TAX' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear factura de venta', description = 'Permiso para crear factura de venta' WHERE code = 'CREATE_SALES_INVOICE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear terceros', description = 'Permiso para crear terceros' WHERE code = 'CREATE_THIRD_PARTIES' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Crear tercero', description = 'Permiso para crear tercero' WHERE code = 'CREATE_THIRD_PARTY' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar factura de compra', description = 'Permiso para eliminar factura de compra' WHERE code = 'DELETE_AP_INVOICE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar activo fijo', description = 'Permiso para eliminar activo fijo' WHERE code = 'DELETE_ASSET' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar banco', description = 'Permiso para eliminar banco' WHERE code = 'DELETE_BANK' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar cuenta bancaria', description = 'Permiso para eliminar cuenta bancaria' WHERE code = 'DELETE_BANK_ACCOUNT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar sucursal bancaria', description = 'Permiso para eliminar sucursal bancaria' WHERE code = 'DELETE_BANK_BRANCH' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar cheque', description = 'Permiso para eliminar cheque' WHERE code = 'DELETE_BANK_CHECK' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar caja', description = 'Permiso para eliminar caja' WHERE code = 'DELETE_CASH' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar chequera', description = 'Permiso para eliminar chequera' WHERE code = 'DELETE_CHECKBOOK' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar datos comerciales', description = 'Permiso para eliminar datos comerciales' WHERE code = 'DELETE_COMMERCIAL_DATA' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar resolucion DIAN', description = 'Permiso para eliminar resolucion dian' WHERE code = 'DELETE_DIAN_RESOLUTION' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar tasa de cambio', description = 'Permiso para eliminar tasa de cambio' WHERE code = 'DELETE_EXCHANGE_RATES' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar factura', description = 'Permiso para eliminar factura' WHERE code = 'DELETE_INVOICE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar permiso', description = 'Permiso para eliminar permiso' WHERE code = 'DELETE_PERMISSION' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar orden de compra', description = 'Permiso para eliminar orden de compra' WHERE code = 'DELETE_PURCHASE_ORDER' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar plantillas de reporte', description = 'Permiso para eliminar plantillas de reporte' WHERE code = 'DELETE_REPORT_TEMPLATES' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar tipos de reporte', description = 'Permiso para eliminar tipos de reporte' WHERE code = 'DELETE_REPORT_TYPES' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar regla tributaria', description = 'Permiso para eliminar regla tributaria' WHERE code = 'DELETE_RULER_TAX' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar factura de venta', description = 'Permiso para eliminar factura de venta' WHERE code = 'DELETE_SALES_INVOICE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar terceros', description = 'Permiso para eliminar terceros' WHERE code = 'DELETE_THIRD_PARTIES' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Eliminar tercero', description = 'Permiso para eliminar tercero' WHERE code = 'DELETE_THIRD_PARTY' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Exportar tercero', description = 'Permiso para exportar tercero' WHERE code = 'EXPORT_THIRD_PARTY' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Gestionar estado de roles de terceros', description = 'Permiso para gestionar estado de roles de terceros' WHERE code = 'MANAGE_THIRD_PARTY_ROLES_STATUS' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar anticipo a proveedor', description = 'Permiso para consultar anticipo a proveedor' WHERE code = 'READ_AP_ADVANCE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar factura de compra', description = 'Permiso para consultar factura de compra' WHERE code = 'READ_AP_INVOICE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar nota de proveedor', description = 'Permiso para consultar nota de proveedor' WHERE code = 'READ_AP_NOTE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar pago a proveedor', description = 'Permiso para consultar pago a proveedor' WHERE code = 'READ_AP_PAYMENT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar reporte de cuentas por pagar', description = 'Permiso para consultar reporte de cuentas por pagar' WHERE code = 'READ_AP_REPORT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar anticipo de cliente', description = 'Permiso para consultar anticipo de cliente' WHERE code = 'READ_AR_ADVANCE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar nota de cliente', description = 'Permiso para consultar nota de cliente' WHERE code = 'READ_AR_NOTE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar cobro', description = 'Permiso para consultar cobro' WHERE code = 'READ_AR_PAYMENT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar envios DIAN', description = 'Permiso para consultar envios dian' WHERE code = 'READ_DIAN' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar reporte DIAN', description = 'Permiso para consultar reporte dian' WHERE code = 'READ_DIAN_REPORT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar resolucion DIAN', description = 'Permiso para consultar resolucion dian' WHERE code = 'READ_DIAN_RESOLUTION' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar recepcion de mercancia', description = 'Permiso para consultar recepcion de mercancia' WHERE code = 'READ_GOODS_RECEIPT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar factura', description = 'Permiso para consultar factura' WHERE code = 'READ_INVOICE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar orden de compra', description = 'Permiso para consultar orden de compra' WHERE code = 'READ_PURCHASE_ORDER' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Consultar factura de venta', description = 'Permiso para consultar factura de venta' WHERE code = 'READ_SALES_INVOICE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Conciliar cheque', description = 'Permiso para conciliar cheque' WHERE code = 'RECONCILE_BANK_CHECK' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Reportar como perdido cheque', description = 'Permiso para reportar como perdido cheque' WHERE code = 'REPORT_LOST_BANK_CHECK' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Buscar comprobante', description = 'Permiso para buscar comprobante' WHERE code = 'SEARCH_VOUCHER' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Enviar factura electronica a DIAN', description = 'Permiso para enviar factura electronica a dian' WHERE code = 'SUBMIT_DIAN' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar factura de compra', description = 'Permiso para actualizar factura de compra' WHERE code = 'UPDATE_AP_INVOICE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar pago a proveedor', description = 'Permiso para actualizar pago a proveedor' WHERE code = 'UPDATE_AP_PAYMENT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar activo fijo', description = 'Permiso para actualizar activo fijo' WHERE code = 'UPDATE_ASSET' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar banco', description = 'Permiso para actualizar banco' WHERE code = 'UPDATE_BANK' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar cuenta bancaria', description = 'Permiso para actualizar cuenta bancaria' WHERE code = 'UPDATE_BANK_ACCOUNT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar sucursal bancaria', description = 'Permiso para actualizar sucursal bancaria' WHERE code = 'UPDATE_BANK_BRANCH' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar caja', description = 'Permiso para actualizar caja' WHERE code = 'UPDATE_CASH' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar chequera', description = 'Permiso para actualizar chequera' WHERE code = 'UPDATE_CHECKBOOK' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar datos comerciales', description = 'Permiso para actualizar datos comerciales' WHERE code = 'UPDATE_COMMERCIAL_DATA' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar resolucion DIAN', description = 'Permiso para actualizar resolucion dian' WHERE code = 'UPDATE_DIAN_RESOLUTION' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Modificar tasa de cambio', description = 'Permiso para modificar tasa de cambio' WHERE code = 'UPDATE_EXCHANGE_RATES' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar recepcion de mercancia', description = 'Permiso para actualizar recepcion de mercancia' WHERE code = 'UPDATE_GOODS_RECEIPT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar factura', description = 'Permiso para actualizar factura' WHERE code = 'UPDATE_INVOICE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar orden de compra', description = 'Permiso para actualizar orden de compra' WHERE code = 'UPDATE_PURCHASE_ORDER' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar tipos de reporte', description = 'Permiso para actualizar tipos de reporte' WHERE code = 'UPDATE_REPORT_TYPES' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar regla tributaria', description = 'Permiso para actualizar regla tributaria' WHERE code = 'UPDATE_RULER_TAX' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar factura de venta', description = 'Permiso para actualizar factura de venta' WHERE code = 'UPDATE_SALES_INVOICE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Actualizar tercero', description = 'Permiso para actualizar tercero' WHERE code = 'UPDATE_THIRD_PARTY' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver factura de compra', description = 'Permiso para ver factura de compra' WHERE code = 'VIEW_AP_INVOICE' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver activo fijo', description = 'Permiso para ver activo fijo' WHERE code = 'VIEW_ASSET' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver banco', description = 'Permiso para ver banco' WHERE code = 'VIEW_BANK' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver cuenta bancaria', description = 'Permiso para ver cuenta bancaria' WHERE code = 'VIEW_BANK_ACCOUNT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver sucursal bancaria', description = 'Permiso para ver sucursal bancaria' WHERE code = 'VIEW_BANK_BRANCH' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver cheque', description = 'Permiso para ver cheque' WHERE code = 'VIEW_BANK_CHECK' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver caja', description = 'Permiso para ver caja' WHERE code = 'VIEW_CASH' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver chequera', description = 'Permiso para ver chequera' WHERE code = 'VIEW_CHECKBOOK' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver datos comerciales', description = 'Permiso para ver datos comerciales' WHERE code = 'VIEW_COMMERCIAL_DATA' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver segmento ECL', description = 'Permiso para ver segmento ecl' WHERE code = 'VIEW_ECL_SEGMENT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Listar tasas de cambio', description = 'Permiso para listar tasas de cambio' WHERE code = 'VIEW_EXCHANGE_RATES' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver forma de pago', description = 'Permiso para ver forma de pago' WHERE code = 'VIEW_PAYMENT_FORM' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver plazo de pago', description = 'Permiso para ver plazo de pago' WHERE code = 'VIEW_PAYMENT_TERM' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver plantillas de reporte', description = 'Permiso para ver plantillas de reporte' WHERE code = 'VIEW_REPORT_TEMPLATES' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver tipos de reporte', description = 'Permiso para ver tipos de reporte' WHERE code = 'VIEW_REPORT_TYPES' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver regla tributaria', description = 'Permiso para ver regla tributaria' WHERE code = 'VIEW_RULER_TAX' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver reporte tributario', description = 'Permiso para ver reporte tributario' WHERE code = 'VIEW_TAX_REPORT' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver terceros', description = 'Permiso para ver terceros' WHERE code = 'VIEW_THIRD_PARTIES' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver tercero', description = 'Permiso para ver tercero' WHERE code = 'VIEW_THIRD_PARTY' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver tipos de organizacion', description = 'Permiso para ver tipos de organizacion' WHERE code = 'VIEW_TYPES_ORGANIZATIONS' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver tipos de regimen', description = 'Permiso para ver tipos de regimen' WHERE code = 'VIEW_TYPES_REGIMES' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Ver retenciones', description = 'Permiso para ver retenciones' WHERE code = 'VIEW_WITHHOLDINGS' AND deleted_at IS NULL;
UPDATE permissions SET name = 'Anular cheque', description = 'Permiso para anular cheque' WHERE code = 'VOID_BANK_CHECK' AND deleted_at IS NULL;

-- ============================================================================
-- 2. Permisos 'puerta de entrada' para los 4 modulos sin granularidad previa
-- ============================================================================
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_ACCOUNTING', 'Ver contabilidad general', 'Permiso para consultar comprobantes, libros oficiales, estados financieros y cierres', 'READ', 7, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_ACCOUNTING' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_AUDIT', 'Ver auditoria', 'Permiso para consultar logs, dashboard y evidencias de auditoria', 'READ', 11, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_AUDIT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_INTEGRATION', 'Ver integracion AAEF', 'Permiso para consultar lotes y transferencias AAEF recibidos de AgroFusion', 'READ', 9, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_INTEGRATION' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_PAYROLL', 'Ver nomina', 'Permiso para consultar empleados, conceptos, recibos y reportes de nomina', 'READ', 10, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_PAYROLL' AND deleted_at IS NULL);

-- ============================================================================
-- 3. Asignar los nuevos permisos a los roles
-- ============================================================================

-- CONTADOR: puede consultar contabilidad general y nomina (no toca integracion/auditoria)
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
 WHERE r.name = 'CONTADOR' AND r.deleted_at IS NULL
   AND p.deleted_at IS NULL
   AND p.code IN ('VIEW_ACCOUNTING', 'VIEW_PAYROLL')
 ON CONFLICT (role_id, permission_id) DO NOTHING;

-- AUDITOR: puede ver TODOS los modulos (es el auditor del sistema)
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
 WHERE r.name = 'AUDITOR' AND r.deleted_at IS NULL
   AND p.deleted_at IS NULL
   AND p.code IN ('VIEW_ACCOUNTING', 'VIEW_AUDIT', 'VIEW_INTEGRATION', 'VIEW_PAYROLL')
 ON CONFLICT (role_id, permission_id) DO NOTHING;
